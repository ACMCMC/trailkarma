import * as anchor from "@coral-xyz/anchor";
import BN from "bn.js";
import {
  Connection,
  Keypair,
  PublicKey,
  SystemProgram,
  Transaction,
  sendAndConfirmTransaction,
} from "@solana/web3.js";
import {
  ExtensionType,
  TOKEN_2022_PROGRAM_ID,
  createInitializeMint2Instruction,
  createInitializeNonTransferableMintInstruction,
  createInitializePermanentDelegateInstruction,
  getMintLen,
} from "@solana/spl-token";
import bs58 from "bs58";
import fs from "node:fs";
import { config } from "./config.js";

function keypairFromEnv(name: string, fallback?: string): Keypair {
  const value = process.env[name] ?? fallback;
  if (!value) {
    throw new Error(`Missing ${name}`);
  }
  return Keypair.fromSecretKey(bs58.decode(value));
}

async function main() {
  const connection = new Connection(config.solanaRpcUrl, "confirmed");
  const sponsor = keypairFromEnv("SPONSOR_SECRET_KEY");
  const attestor = keypairFromEnv("ATTESTOR_SECRET_KEY");
  const admin = keypairFromEnv("ADMIN_SECRET_KEY", process.env.SPONSOR_SECRET_KEY);

  const idl = JSON.parse(fs.readFileSync(config.idlPath, "utf8")) as anchor.Idl & { address?: string };
  idl.address = config.programId.toBase58();
  const provider = new anchor.AnchorProvider(
    connection,
    new anchor.Wallet(admin),
    anchor.AnchorProvider.defaultOptions(),
  );
  const program = new anchor.Program(idl, provider) as any;

  const configPda = PublicKey.findProgramAddressSync([Buffer.from("config")], config.programId)[0];

  const karmaMint = await createMint({
    connection,
    payer: sponsor,
    mintAuthority: configPda,
    decimals: 0,
    extensions: [ExtensionType.PermanentDelegate],
    permanentDelegate: configPda,
  });

  const trailScoutMint = await createMint({
    connection,
    payer: sponsor,
    mintAuthority: configPda,
    decimals: 0,
    extensions: [ExtensionType.NonTransferable],
  });
  const relayRangerMint = await createMint({
    connection,
    payer: sponsor,
    mintAuthority: configPda,
    decimals: 0,
    extensions: [ExtensionType.NonTransferable],
  });
  const speciesSpotterMint = await createMint({
    connection,
    payer: sponsor,
    mintAuthority: configPda,
    decimals: 0,
    extensions: [ExtensionType.NonTransferable],
  });
  const waterGuardianMint = await createMint({
    connection,
    payer: sponsor,
    mintAuthority: configPda,
    decimals: 0,
    extensions: [ExtensionType.NonTransferable],
  });
  const hazardHeraldMint = await createMint({
    connection,
    payer: sponsor,
    mintAuthority: configPda,
    decimals: 0,
    extensions: [ExtensionType.NonTransferable],
  });

  const txSignature = await program.methods
    .initializeConfig({
      rewardHazard: new BN(10),
      rewardWater: new BN(10),
      rewardSpecies: new BN(8),
      rewardRelay: new BN(12),
      rewardVerify: new BN(4),
      rewardSpeciesBonus: new BN(5),
      maxRelayExpirySecs: new BN(6 * 60 * 60),
    })
    .accounts({
      admin: admin.publicKey,
      sponsor: sponsor.publicKey,
      attestor: attestor.publicKey,
      config: configPda,
      karmaMint,
      trailScoutMint,
      relayRangerMint,
      speciesSpotterMint,
      waterGuardianMint,
      hazardHeraldMint,
      systemProgram: SystemProgram.programId,
    })
    .signers([admin])
    .rpc();

  console.log(JSON.stringify({
    configPda: configPda.toBase58(),
    txSignature,
    mints: {
      karmaMint: karmaMint.toBase58(),
      trailScoutMint: trailScoutMint.toBase58(),
      relayRangerMint: relayRangerMint.toBase58(),
      speciesSpotterMint: speciesSpotterMint.toBase58(),
      waterGuardianMint: waterGuardianMint.toBase58(),
      hazardHeraldMint: hazardHeraldMint.toBase58(),
    },
  }, null, 2));
}

async function createMint(args: {
  connection: Connection;
  payer: Keypair;
  mintAuthority: PublicKey;
  decimals: number;
  extensions: ExtensionType[];
  permanentDelegate?: PublicKey;
}): Promise<PublicKey> {
  const mint = Keypair.generate();
  const mintLen = getMintLen(args.extensions);
  const lamports = await args.connection.getMinimumBalanceForRentExemption(mintLen);

  const instructions = [
    SystemProgram.createAccount({
      fromPubkey: args.payer.publicKey,
      newAccountPubkey: mint.publicKey,
      lamports,
      space: mintLen,
      programId: TOKEN_2022_PROGRAM_ID,
    }),
  ];

  if (args.extensions.includes(ExtensionType.PermanentDelegate)) {
    if (!args.permanentDelegate) throw new Error("permanentDelegate is required");
    instructions.push(
      createInitializePermanentDelegateInstruction(
        mint.publicKey,
        args.permanentDelegate,
        TOKEN_2022_PROGRAM_ID,
      ),
    );
  }

  if (args.extensions.includes(ExtensionType.NonTransferable)) {
    instructions.push(
      createInitializeNonTransferableMintInstruction(mint.publicKey, TOKEN_2022_PROGRAM_ID),
    );
  }

  instructions.push(
    createInitializeMint2Instruction(
      mint.publicKey,
      args.decimals,
      args.mintAuthority,
      null,
      TOKEN_2022_PROGRAM_ID,
    ),
  );

  const transaction = new Transaction().add(...instructions);
  await sendAndConfirmTransaction(args.connection, transaction, [args.payer, mint]);
  return mint.publicKey;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
