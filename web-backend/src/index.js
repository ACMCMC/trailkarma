require('dotenv').config()
const express = require('express')
const cors = require('cors')
const fs = require('fs')
const path = require('path')
const { marked } = require('marked')

const app = express()
app.use(cors())
app.use(express.json())

app.use('/hiker', require('./routes/hiker'))

// 健康检查
app.get('/health', (req, res) => res.json({ status: 'ok' }))

// API docs viewer
app.get('/api-docs', (_req, res) => {
  const mdPath = path.join(__dirname, '..', 'API_CONTRACT.md')
  const md = fs.readFileSync(mdPath, 'utf8')
  const html = marked(md)
  res.send(`<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>TrailKarma API Docs</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 860px; margin: 40px auto; padding: 0 24px; color: #1a1a1a; line-height: 1.6; }
    h1 { color: #2d6a4f; border-bottom: 2px solid #2d6a4f; padding-bottom: 8px; }
    h2 { color: #40916c; margin-top: 40px; }
    h3 { color: #1b4332; }
    code { background: #f4f4f4; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }
    pre { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 8px; overflow-x: auto; }
    pre code { background: none; padding: 0; color: inherit; }
    table { border-collapse: collapse; width: 100%; margin: 16px 0; }
    th { background: #2d6a4f; color: white; padding: 10px 14px; text-align: left; }
    td { padding: 9px 14px; border-bottom: 1px solid #e0e0e0; }
    tr:hover td { background: #f9f9f9; }
    blockquote { border-left: 4px solid #2d6a4f; margin: 0; padding: 8px 16px; background: #f0faf4; color: #555; }
    input[type=checkbox] { margin-right: 6px; }
    hr { border: none; border-top: 1px solid #ddd; margin: 32px 0; }
  </style>
</head>
<body>${html}</body>
</html>`)
})

const PORT = process.env.PORT || 3000
app.listen(PORT, () => console.log(`TrailKarma backend running on port ${PORT}`))
