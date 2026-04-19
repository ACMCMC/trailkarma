use anchor_lang::prelude::*;
use anchor_spl::associated_token::AssociatedToken;
use anchor_spl::token_interface::{
    self, Mint, MintTo, TokenAccount, TokenInterface, TransferChecked,
};
use solana_instructions_sysvar::{
    load_current_index_checked, load_instruction_at_checked,
};
use solana_sdk_ids::ed25519_program;

declare_id!("GmRtJ6ghkm26SfdTDXTFDoX4TzHgQstCjwPPs5EpdZGS");

const RELAY_MESSAGE_KIND: u8 = 1;
const TIP_MESSAGE_KIND: u8 = 2;
const PAYLOAD_VERSION: u8 = 1;

#[program]
pub mod trail_karma_rewards {
    use super::*;

    pub fn initialize_config(
        ctx: Context<InitializeConfig>,
        args: InitializeConfigArgs,
    ) -> Result<()> {
        let config = &mut ctx.accounts.config;
        config.admin = ctx.accounts.admin.key();
        config.sponsor = ctx.accounts.sponsor.key();
        config.attestor = ctx.accounts.attestor.key();
        config.karma_mint = ctx.accounts.karma_mint.key();
        config.trail_scout_mint = ctx.accounts.trail_scout_mint.key();
        config.relay_ranger_mint = ctx.accounts.relay_ranger_mint.key();
        config.species_spotter_mint = ctx.accounts.species_spotter_mint.key();
        config.water_guardian_mint = ctx.accounts.water_guardian_mint.key();
        config.hazard_herald_mint = ctx.accounts.hazard_herald_mint.key();
        config.reward_hazard = args.reward_hazard;
        config.reward_water = args.reward_water;
        config.reward_species = args.reward_species;
        config.reward_relay = args.reward_relay;
        config.reward_verify = args.reward_verify;
        config.reward_species_bonus = args.reward_species_bonus;
        config.max_relay_expiry_secs = args.max_relay_expiry_secs;
        config.bump = ctx.bumps.config;
        Ok(())
    }

    pub fn register_user(ctx: Context<RegisterUser>, args: RegisterUserArgs) -> Result<()> {
        let profile = &mut ctx.accounts.user_profile;
        profile.wallet = ctx.accounts.user_wallet.key();
        profile.app_user_id_hash = args.app_user_id_hash;
        profile.total_karma_earned = 0;
        profile.total_verified_contributions = 0;
        profile.hazard_count = 0;
        profile.water_count = 0;
        profile.species_count = 0;
        profile.relay_fulfill_count = 0;
        profile.verification_count = 0;
        profile.bump = ctx.bumps.user_profile;
        Ok(())
    }

    pub fn create_relay_job_from_intent(
        ctx: Context<CreateRelayJobFromIntent>,
        args: CreateRelayJobArgs,
    ) -> Result<()> {
        let config = &ctx.accounts.config;
        let sender = ctx.accounts.user_wallet.key();
        let relay_message = build_relay_message(
            &args.job_id,
            &sender,
            &args.destination_hash,
            &args.payload_hash,
            args.expiry_ts,
            args.reward_amount,
            args.nonce,
        );

        require!(args.signed_message == relay_message, ErrorCode::SignedPayloadMismatch);
        verify_previous_ed25519(
            &ctx.accounts.instructions.to_account_info(),
            sender,
            &args.signed_message,
        )?;

        let now = Clock::get()?.unix_timestamp;
        require!(args.expiry_ts > now, ErrorCode::RelayIntentExpired);
        require!(
            args.expiry_ts <= now + config.max_relay_expiry_secs,
            ErrorCode::RelayIntentTooFarOut
        );
        require_eq!(args.reward_amount, config.reward_relay, ErrorCode::UnexpectedRelayReward);

        let relay_job = &mut ctx.accounts.relay_job;
        relay_job.job_id = args.job_id;
        relay_job.sender = sender;
        relay_job.destination_hash = args.destination_hash;
        relay_job.payload_hash = args.payload_hash;
        relay_job.expiry_ts = args.expiry_ts;
        relay_job.reward_amount = args.reward_amount;
        relay_job.status = RelayJobStatus::Open as u8;
        relay_job.fulfiller = Pubkey::default();
        relay_job.created_at = now;
        relay_job.fulfilled_at = 0;
        relay_job.bump = ctx.bumps.relay_job;
        Ok(())
    }

    pub fn fulfill_relay_job(
        ctx: Context<FulfillRelayJob>,
        args: FulfillRelayJobArgs,
    ) -> Result<()> {
        let config = &ctx.accounts.config;
        require_keys_eq!(ctx.accounts.attestor.key(), config.attestor, ErrorCode::UnauthorizedAttestor);

        let relay_job = &mut ctx.accounts.relay_job;
        require_eq!(relay_job.status, RelayJobStatus::Open as u8, ErrorCode::RelayAlreadyFulfilled);

        let now = Clock::get()?.unix_timestamp;
        require!(relay_job.expiry_ts >= now, ErrorCode::RelayJobExpired);

        relay_job.status = RelayJobStatus::Fulfilled as u8;
        relay_job.fulfiller = ctx.accounts.fulfiller_wallet.key();
        relay_job.fulfilled_at = now;

        let receipt = &mut ctx.accounts.reward_receipt;
        receipt.receipt_id = args.receipt_id;
        receipt.user_wallet = ctx.accounts.fulfiller_wallet.key();
        receipt.event_type = EventType::Relay as u8;
        receipt.verification_tier = args.verification_tier as u8;
        receipt.reward_amount = config.reward_relay;
        receipt.metadata_hash = args.proof_hash;
        receipt.attestor = ctx.accounts.attestor.key();
        receipt.claimed_at = now;
        receipt.bump = ctx.bumps.reward_receipt;

        let profile = &mut ctx.accounts.fulfiller_profile;
        profile.total_karma_earned = profile.total_karma_earned.saturating_add(config.reward_relay);
        profile.total_verified_contributions = profile.total_verified_contributions.saturating_add(1);
        profile.relay_fulfill_count = profile.relay_fulfill_count.saturating_add(1);

        mint_karma(
            &ctx.accounts.config,
            &ctx.accounts.karma_mint,
            &ctx.accounts.fulfiller_karma_ata,
            &ctx.accounts.token_program,
            config.reward_relay,
        )?;

        Ok(())
    }

    pub fn claim_contribution_reward(
        ctx: Context<ClaimContributionReward>,
        args: ClaimContributionArgs,
    ) -> Result<()> {
        let config = &ctx.accounts.config;
        require_keys_eq!(ctx.accounts.attestor.key(), config.attestor, ErrorCode::UnauthorizedAttestor);

        let reward = reward_for_contribution(config, &args);
        let now = Clock::get()?.unix_timestamp;

        let receipt = &mut ctx.accounts.contribution_receipt;
        receipt.receipt_id = args.contribution_id;
        receipt.user_wallet = ctx.accounts.user_wallet.key();
        receipt.event_type = args.event_type as u8;
        receipt.verification_tier = args.verification_tier as u8;
        receipt.reward_amount = reward;
        receipt.metadata_hash = args.metadata_hash;
        receipt.attestor = ctx.accounts.attestor.key();
        receipt.claimed_at = now;
        receipt.bump = ctx.bumps.contribution_receipt;

        let profile = &mut ctx.accounts.user_profile;
        profile.total_karma_earned = profile.total_karma_earned.saturating_add(reward);
        profile.total_verified_contributions = profile.total_verified_contributions.saturating_add(1);

        match args.event_type {
            EventType::Hazard => profile.hazard_count = profile.hazard_count.saturating_add(1),
            EventType::Water => profile.water_count = profile.water_count.saturating_add(1),
            EventType::Species => profile.species_count = profile.species_count.saturating_add(1),
            EventType::Verification => profile.verification_count = profile.verification_count.saturating_add(1),
            EventType::Relay => profile.relay_fulfill_count = profile.relay_fulfill_count.saturating_add(1),
        }

        mint_karma(
            &ctx.accounts.config,
            &ctx.accounts.karma_mint,
            &ctx.accounts.user_karma_ata,
            &ctx.accounts.token_program,
            reward,
        )?;

        Ok(())
    }

    pub fn claim_badge(ctx: Context<ClaimBadge>, args: ClaimBadgeArgs) -> Result<()> {
        let config = &ctx.accounts.config;
        let profile = &ctx.accounts.user_profile;
        require!(badge_eligible(profile, args.badge_code), ErrorCode::BadgeNotEligible);
        require!(
            badge_mint_for_code(config, args.badge_code) == ctx.accounts.badge_mint.key(),
            ErrorCode::BadgeMintMismatch
        );

        let badge_claim = &mut ctx.accounts.badge_claim;
        badge_claim.user_wallet = ctx.accounts.user_wallet.key();
        badge_claim.badge_code = args.badge_code as u8;
        badge_claim.claimed_at = Clock::get()?.unix_timestamp;
        badge_claim.bump = ctx.bumps.badge_claim;

        mint_badge(
            &ctx.accounts.config,
            &ctx.accounts.badge_mint,
            &ctx.accounts.user_badge_ata,
            &ctx.accounts.token_program,
        )?;

        Ok(())
    }

    pub fn tip_karma(ctx: Context<TipKarma>, args: TipKarmaArgs) -> Result<()> {
        let sender = ctx.accounts.sender_wallet.key();
        let recipient = ctx.accounts.recipient_wallet.key();
        let tip_message = build_tip_message(&args.tip_id, &sender, &recipient, args.amount, args.nonce);
        require!(args.signed_message == tip_message, ErrorCode::SignedPayloadMismatch);
        verify_previous_ed25519(
            &ctx.accounts.instructions.to_account_info(),
            sender,
            &args.signed_message,
        )?;

        let tip_receipt = &mut ctx.accounts.tip_receipt;
        tip_receipt.tip_id = args.tip_id;
        tip_receipt.sender = sender;
        tip_receipt.recipient = recipient;
        tip_receipt.amount = args.amount;
        tip_receipt.created_at = Clock::get()?.unix_timestamp;
        tip_receipt.bump = ctx.bumps.tip_receipt;

        let signer_seeds: &[&[u8]] = &[b"config", &[ctx.accounts.config.bump]];
        let cpi_accounts = TransferChecked {
            from: ctx.accounts.sender_karma_ata.to_account_info(),
            mint: ctx.accounts.karma_mint.to_account_info(),
            to: ctx.accounts.recipient_karma_ata.to_account_info(),
            authority: ctx.accounts.config.to_account_info(),
        };
        let signer_binding = [signer_seeds];
        let cpi_ctx = CpiContext::new(ctx.accounts.token_program.key(), cpi_accounts)
            .with_signer(&signer_binding);
        token_interface::transfer_checked(cpi_ctx, args.amount, ctx.accounts.karma_mint.decimals)?;

        Ok(())
    }
}

fn reward_for_contribution(config: &Config, args: &ClaimContributionArgs) -> u64 {
    let mut reward = match args.event_type {
        EventType::Hazard => config.reward_hazard,
        EventType::Water => config.reward_water,
        EventType::Species => config.reward_species,
        EventType::Relay => config.reward_relay,
        EventType::Verification => config.reward_verify,
    };

    if matches!(args.event_type, EventType::Species) && args.high_confidence_bonus {
        reward = reward.saturating_add(config.reward_species_bonus);
    }

    reward
}

fn badge_eligible(profile: &UserProfile, badge_code: BadgeCode) -> bool {
    match badge_code {
        BadgeCode::TrailScout => profile.total_verified_contributions >= 1,
        BadgeCode::RelayRanger => profile.relay_fulfill_count >= 1,
        BadgeCode::SpeciesSpotter => profile.species_count >= 1,
        BadgeCode::WaterGuardian => profile.water_count >= 5,
        BadgeCode::HazardHerald => profile.hazard_count >= 5,
    }
}

fn badge_mint_for_code(config: &Config, badge_code: BadgeCode) -> Pubkey {
    match badge_code {
        BadgeCode::TrailScout => config.trail_scout_mint,
        BadgeCode::RelayRanger => config.relay_ranger_mint,
        BadgeCode::SpeciesSpotter => config.species_spotter_mint,
        BadgeCode::WaterGuardian => config.water_guardian_mint,
        BadgeCode::HazardHerald => config.hazard_herald_mint,
    }
}

fn mint_karma<'info>(
    config: &Account<'info, Config>,
    karma_mint: &InterfaceAccount<'info, Mint>,
    user_karma_ata: &InterfaceAccount<'info, TokenAccount>,
    token_program: &Interface<'info, TokenInterface>,
    amount: u64,
) -> Result<()> {
    let signer_seeds: &[&[u8]] = &[b"config", &[config.bump]];
    let cpi_accounts = MintTo {
        mint: karma_mint.to_account_info(),
        to: user_karma_ata.to_account_info(),
        authority: config.to_account_info(),
    };
    let signer_binding = [signer_seeds];
    let cpi_ctx = CpiContext::new(token_program.key(), cpi_accounts)
        .with_signer(&signer_binding);
    token_interface::mint_to(cpi_ctx, amount)
}

fn mint_badge<'info>(
    config: &Account<'info, Config>,
    badge_mint: &InterfaceAccount<'info, Mint>,
    user_badge_ata: &InterfaceAccount<'info, TokenAccount>,
    token_program: &Interface<'info, TokenInterface>,
) -> Result<()> {
    let signer_seeds: &[&[u8]] = &[b"config", &[config.bump]];
    let cpi_accounts = MintTo {
        mint: badge_mint.to_account_info(),
        to: user_badge_ata.to_account_info(),
        authority: config.to_account_info(),
    };
    let signer_binding = [signer_seeds];
    let cpi_ctx = CpiContext::new(token_program.key(), cpi_accounts)
        .with_signer(&signer_binding);
    token_interface::mint_to(cpi_ctx, 1)
}

fn build_relay_message(
    job_id: &[u8; 32],
    sender: &Pubkey,
    destination_hash: &[u8; 32],
    payload_hash: &[u8; 32],
    expiry_ts: i64,
    reward_amount: u64,
    nonce: u64,
) -> Vec<u8> {
    let mut out = Vec::with_capacity(154);
    out.push(RELAY_MESSAGE_KIND);
    out.push(PAYLOAD_VERSION);
    out.extend_from_slice(job_id);
    out.extend_from_slice(sender.as_ref());
    out.extend_from_slice(destination_hash);
    out.extend_from_slice(payload_hash);
    out.extend_from_slice(&expiry_ts.to_le_bytes());
    out.extend_from_slice(&reward_amount.to_le_bytes());
    out.extend_from_slice(&nonce.to_le_bytes());
    out
}

fn build_tip_message(
    tip_id: &[u8; 32],
    sender: &Pubkey,
    recipient: &Pubkey,
    amount: u64,
    nonce: u64,
) -> Vec<u8> {
    let mut out = Vec::with_capacity(114);
    out.push(TIP_MESSAGE_KIND);
    out.push(PAYLOAD_VERSION);
    out.extend_from_slice(tip_id);
    out.extend_from_slice(sender.as_ref());
    out.extend_from_slice(recipient.as_ref());
    out.extend_from_slice(&amount.to_le_bytes());
    out.extend_from_slice(&nonce.to_le_bytes());
    out
}

fn verify_previous_ed25519(
    instructions_sysvar: &AccountInfo,
    expected_pubkey: Pubkey,
    expected_message: &[u8],
) -> Result<()> {
    let current_index = load_current_index_checked(instructions_sysvar)? as usize;
    require!(current_index > 0, ErrorCode::MissingEd25519Instruction);

    let ix = load_instruction_at_checked(current_index - 1, instructions_sysvar)?;
    require_keys_eq!(ix.program_id, ed25519_program::id(), ErrorCode::MissingEd25519Instruction);
    require!(ix.data.len() >= 16, ErrorCode::InvalidEd25519Instruction);
    require_eq!(ix.data[0], 1, ErrorCode::InvalidEd25519Instruction);

    let public_key_offset = u16::from_le_bytes([ix.data[6], ix.data[7]]) as usize;
    let public_key_ix_index = u16::from_le_bytes([ix.data[8], ix.data[9]]);
    let message_offset = u16::from_le_bytes([ix.data[10], ix.data[11]]) as usize;
    let message_size = u16::from_le_bytes([ix.data[12], ix.data[13]]) as usize;
    let message_ix_index = u16::from_le_bytes([ix.data[14], ix.data[15]]);

    require!(
        public_key_ix_index == u16::MAX && message_ix_index == u16::MAX,
        ErrorCode::InvalidEd25519Instruction
    );
    require!(
        ix.data.len() >= public_key_offset + 32 && ix.data.len() >= message_offset + message_size,
        ErrorCode::InvalidEd25519Instruction
    );

    let public_key_bytes = &ix.data[public_key_offset..public_key_offset + 32];
    let signed_message = &ix.data[message_offset..message_offset + message_size];

    require!(public_key_bytes == expected_pubkey.as_ref(), ErrorCode::SignerMismatch);
    require!(signed_message == expected_message, ErrorCode::SignedPayloadMismatch);
    Ok(())
}

#[derive(Accounts)]
pub struct InitializeConfig<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,
    /// CHECK: sponsor only stored in config
    pub sponsor: UncheckedAccount<'info>,
    /// CHECK: attestor only stored in config
    pub attestor: UncheckedAccount<'info>,
    #[account(
        init,
        payer = admin,
        space = 8 + Config::INIT_SPACE,
        seeds = [b"config"],
        bump
    )]
    pub config: Account<'info, Config>,
    pub karma_mint: InterfaceAccount<'info, Mint>,
    pub trail_scout_mint: InterfaceAccount<'info, Mint>,
    pub relay_ranger_mint: InterfaceAccount<'info, Mint>,
    pub species_spotter_mint: InterfaceAccount<'info, Mint>,
    pub water_guardian_mint: InterfaceAccount<'info, Mint>,
    pub hazard_herald_mint: InterfaceAccount<'info, Mint>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct RegisterUser<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    pub config: Account<'info, Config>,
    #[account(
        init,
        payer = payer,
        space = 8 + UserProfile::INIT_SPACE,
        seeds = [b"user_profile", user_wallet.key().as_ref()],
        bump
    )]
    pub user_profile: Account<'info, UserProfile>,
    /// CHECK: user wallet address is the authority for token accounts
    pub user_wallet: UncheckedAccount<'info>,
    pub karma_mint: InterfaceAccount<'info, Mint>,
    #[account(
        init_if_needed,
        payer = payer,
        associated_token::mint = karma_mint,
        associated_token::authority = user_wallet,
        associated_token::token_program = token_program
    )]
    pub user_karma_ata: InterfaceAccount<'info, TokenAccount>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(args: CreateRelayJobArgs)]
pub struct CreateRelayJobFromIntent<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    pub config: Account<'info, Config>,
    #[account(
        seeds = [b"user_profile", user_wallet.key().as_ref()],
        bump = user_profile.bump
    )]
    pub user_profile: Account<'info, UserProfile>,
    /// CHECK: verified via ed25519 instruction and PDA mapping
    pub user_wallet: UncheckedAccount<'info>,
    #[account(
        init,
        payer = payer,
        space = 8 + RelayJob::INIT_SPACE,
        seeds = [b"relay_job", args.job_id.as_ref()],
        bump
    )]
    pub relay_job: Account<'info, RelayJob>,
    /// CHECK: instructions sysvar for ed25519 verification
    #[account(address = solana_instructions_sysvar::ID)]
    pub instructions: UncheckedAccount<'info>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(args: FulfillRelayJobArgs)]
pub struct FulfillRelayJob<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    pub attestor: Signer<'info>,
    pub config: Box<Account<'info, Config>>,
    #[account(mut)]
    pub relay_job: Box<Account<'info, RelayJob>>,
    #[account(
        init,
        payer = payer,
        space = 8 + ContributionReceipt::INIT_SPACE,
        seeds = [b"contribution_receipt", args.receipt_id.as_ref()],
        bump
    )]
    pub reward_receipt: Box<Account<'info, ContributionReceipt>>,
    #[account(
        mut,
        seeds = [b"user_profile", fulfiller_wallet.key().as_ref()],
        bump = fulfiller_profile.bump
    )]
    pub fulfiller_profile: Box<Account<'info, UserProfile>>,
    /// CHECK: wallet mapped by fulfiller profile
    pub fulfiller_wallet: UncheckedAccount<'info>,
    #[account(mut, address = config.karma_mint)]
    pub karma_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(
        init_if_needed,
        payer = payer,
        associated_token::mint = karma_mint,
        associated_token::authority = fulfiller_wallet,
        associated_token::token_program = token_program
    )]
    pub fulfiller_karma_ata: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(args: ClaimContributionArgs)]
pub struct ClaimContributionReward<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    pub attestor: Signer<'info>,
    pub config: Box<Account<'info, Config>>,
    #[account(
        mut,
        seeds = [b"user_profile", user_wallet.key().as_ref()],
        bump = user_profile.bump
    )]
    pub user_profile: Box<Account<'info, UserProfile>>,
    /// CHECK: wallet mapped by user profile
    pub user_wallet: UncheckedAccount<'info>,
    #[account(
        init,
        payer = payer,
        space = 8 + ContributionReceipt::INIT_SPACE,
        seeds = [b"contribution_receipt", args.contribution_id.as_ref()],
        bump
    )]
    pub contribution_receipt: Box<Account<'info, ContributionReceipt>>,
    #[account(mut, address = config.karma_mint)]
    pub karma_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(
        init_if_needed,
        payer = payer,
        associated_token::mint = karma_mint,
        associated_token::authority = user_wallet,
        associated_token::token_program = token_program
    )]
    pub user_karma_ata: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(args: ClaimBadgeArgs)]
pub struct ClaimBadge<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    pub config: Box<Account<'info, Config>>,
    #[account(
        seeds = [b"user_profile", user_wallet.key().as_ref()],
        bump = user_profile.bump
    )]
    pub user_profile: Box<Account<'info, UserProfile>>,
    /// CHECK: wallet mapped by user profile
    pub user_wallet: UncheckedAccount<'info>,
    #[account(
        init,
        payer = payer,
        space = 8 + BadgeClaim::INIT_SPACE,
        seeds = [b"badge_claim", user_wallet.key().as_ref(), &[args.badge_code as u8]],
        bump
    )]
    pub badge_claim: Box<Account<'info, BadgeClaim>>,
    #[account(mut)]
    pub badge_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(
        init_if_needed,
        payer = payer,
        associated_token::mint = badge_mint,
        associated_token::authority = user_wallet,
        associated_token::token_program = token_program
    )]
    pub user_badge_ata: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(args: TipKarmaArgs)]
pub struct TipKarma<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,
    pub config: Box<Account<'info, Config>>,
    #[account(
        seeds = [b"user_profile", sender_wallet.key().as_ref()],
        bump = sender_profile.bump
    )]
    pub sender_profile: Box<Account<'info, UserProfile>>,
    #[account(
        seeds = [b"user_profile", recipient_wallet.key().as_ref()],
        bump = recipient_profile.bump
    )]
    pub recipient_profile: Box<Account<'info, UserProfile>>,
    /// CHECK: signature-verified sender wallet
    pub sender_wallet: UncheckedAccount<'info>,
    /// CHECK: recipient wallet tied to PDA
    pub recipient_wallet: UncheckedAccount<'info>,
    #[account(
        init,
        payer = payer,
        space = 8 + TipReceipt::INIT_SPACE,
        seeds = [b"tip_receipt", args.tip_id.as_ref()],
        bump
    )]
    pub tip_receipt: Box<Account<'info, TipReceipt>>,
    #[account(mut, address = config.karma_mint)]
    pub karma_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut)]
    pub sender_karma_ata: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(
        init_if_needed,
        payer = payer,
        associated_token::mint = karma_mint,
        associated_token::authority = recipient_wallet,
        associated_token::token_program = token_program
    )]
    pub recipient_karma_ata: Box<InterfaceAccount<'info, TokenAccount>>,
    /// CHECK: instructions sysvar for ed25519 verification
    #[account(address = solana_instructions_sysvar::ID)]
    pub instructions: UncheckedAccount<'info>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy)]
pub struct InitializeConfigArgs {
    pub reward_hazard: u64,
    pub reward_water: u64,
    pub reward_species: u64,
    pub reward_relay: u64,
    pub reward_verify: u64,
    pub reward_species_bonus: u64,
    pub max_relay_expiry_secs: i64,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy)]
pub struct RegisterUserArgs {
    pub app_user_id_hash: [u8; 32],
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone)]
pub struct CreateRelayJobArgs {
    pub job_id: [u8; 32],
    pub destination_hash: [u8; 32],
    pub payload_hash: [u8; 32],
    pub expiry_ts: i64,
    pub reward_amount: u64,
    pub nonce: u64,
    pub signed_message: Vec<u8>,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy)]
pub struct FulfillRelayJobArgs {
    pub receipt_id: [u8; 32],
    pub proof_hash: [u8; 32],
    pub verification_tier: VerificationTier,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy)]
pub struct ClaimContributionArgs {
    pub contribution_id: [u8; 32],
    pub event_type: EventType,
    pub verification_tier: VerificationTier,
    pub metadata_hash: [u8; 32],
    pub high_confidence_bonus: bool,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy)]
pub struct ClaimBadgeArgs {
    pub badge_code: BadgeCode,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone)]
pub struct TipKarmaArgs {
    pub tip_id: [u8; 32],
    pub amount: u64,
    pub nonce: u64,
    pub signed_message: Vec<u8>,
}

#[account]
#[derive(InitSpace)]
pub struct Config {
    pub admin: Pubkey,
    pub sponsor: Pubkey,
    pub attestor: Pubkey,
    pub karma_mint: Pubkey,
    pub trail_scout_mint: Pubkey,
    pub relay_ranger_mint: Pubkey,
    pub species_spotter_mint: Pubkey,
    pub water_guardian_mint: Pubkey,
    pub hazard_herald_mint: Pubkey,
    pub reward_hazard: u64,
    pub reward_water: u64,
    pub reward_species: u64,
    pub reward_relay: u64,
    pub reward_verify: u64,
    pub reward_species_bonus: u64,
    pub max_relay_expiry_secs: i64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct UserProfile {
    pub wallet: Pubkey,
    pub app_user_id_hash: [u8; 32],
    pub total_karma_earned: u64,
    pub total_verified_contributions: u32,
    pub hazard_count: u32,
    pub water_count: u32,
    pub species_count: u32,
    pub relay_fulfill_count: u32,
    pub verification_count: u32,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct ContributionReceipt {
    pub receipt_id: [u8; 32],
    pub user_wallet: Pubkey,
    pub event_type: u8,
    pub verification_tier: u8,
    pub reward_amount: u64,
    pub metadata_hash: [u8; 32],
    pub attestor: Pubkey,
    pub claimed_at: i64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct RelayJob {
    pub job_id: [u8; 32],
    pub sender: Pubkey,
    pub destination_hash: [u8; 32],
    pub payload_hash: [u8; 32],
    pub expiry_ts: i64,
    pub reward_amount: u64,
    pub status: u8,
    pub fulfiller: Pubkey,
    pub created_at: i64,
    pub fulfilled_at: i64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct BadgeClaim {
    pub user_wallet: Pubkey,
    pub badge_code: u8,
    pub claimed_at: i64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct TipReceipt {
    pub tip_id: [u8; 32],
    pub sender: Pubkey,
    pub recipient: Pubkey,
    pub amount: u64,
    pub created_at: i64,
    pub bump: u8,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq)]
pub enum EventType {
    Hazard,
    Water,
    Species,
    Relay,
    Verification,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq)]
pub enum VerificationTier {
    Tier1AutoVerified,
    Tier2ModelAssisted,
    Tier3CommunityVerified,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq)]
pub enum BadgeCode {
    TrailScout,
    RelayRanger,
    SpeciesSpotter,
    WaterGuardian,
    HazardHerald,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq)]
pub enum RelayJobStatus {
    Open,
    Fulfilled,
}

#[error_code]
pub enum ErrorCode {
    #[msg("The attestor is not authorized.")]
    UnauthorizedAttestor,
    #[msg("The relay intent is already expired.")]
    RelayIntentExpired,
    #[msg("The relay intent expiry is too far in the future.")]
    RelayIntentTooFarOut,
    #[msg("The relay reward does not match the configured value.")]
    UnexpectedRelayReward,
    #[msg("The relay job has already been fulfilled.")]
    RelayAlreadyFulfilled,
    #[msg("The relay job is expired.")]
    RelayJobExpired,
    #[msg("The signed payload does not match the expected canonical bytes.")]
    SignedPayloadMismatch,
    #[msg("A required ed25519 verification instruction is missing.")]
    MissingEd25519Instruction,
    #[msg("The ed25519 verification instruction is invalid.")]
    InvalidEd25519Instruction,
    #[msg("The verified signer does not match the expected wallet.")]
    SignerMismatch,
    #[msg("The requested badge is not yet eligible.")]
    BadgeNotEligible,
    #[msg("The badge mint does not match the configured badge.")]
    BadgeMintMismatch,
}
