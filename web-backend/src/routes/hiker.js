const router = require('express').Router()

router.get('/:id/latest', (req, res) => res.json({ ok: true }))

module.exports = router
