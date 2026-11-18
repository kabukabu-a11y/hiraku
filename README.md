# Hiraku

**「URLの開き方」を自分で決められるAndroidブラウザ。**
リンク元のアプリごとに、どのブラウザで・どう開くかをカスタマイズできます。

**An Android browser that lets you decide how every link opens.**
Choose the open method per source app: your favorite browser's Custom Tab, an overlay window that never leaves the current app, floating bubbles, and more.

Based on [Lynket](https://github.com/arunkumar9t2/lynket-browser) (GPLv3). Hiraku is a personal fork with a redesigned link-opening system, a floating overlay browser, and ad blocking.

## ダウンロード / Download

[Releases](https://github.com/kabukabu-a11y/lynket-browser/releases) から最新のAPKをダウンロードしてください。
Download the latest APK from [Releases](https://github.com/kabukabu-a11y/lynket-browser/releases).

インストール後、Hirakuをデフォルトブラウザに設定すると、他のアプリのリンクをHirakuが受け取るようになります。
After installing, set Hiraku as your default browser so links from other apps are routed to it.

> APKから直接インストールしたアプリは、Androidの「制限付き設定」で一部の許可がブロックされることがあります。その場合は、設定 → アプリ → Hiraku → 右上の︙ →「制限付き設定を許可」を押してから許可し直してください。
> If Android blocks a permission with "restricted settings", go to Settings → Apps → Hiraku → ︙ (top right) → "Allow restricted settings", then grant the permission again.

## 特徴 / Features

- **開き方は9通り＋α**：選んだブラウザのCustom Tab／通常モード、2つ目のブラウザ、システム標準のCustom Tab、アプリに留まる重ねて表示、バブル、共有、キャンセル、そして「開くたびに選ぶ」（選択画面の項目は並べ替え・非表示もできます）
  **9 ways to open a link**: Custom Tab or normal mode of your first/second chosen browser, the system default Custom Tab, an overlay window that stays in the current app, bubbles, share, cancel — plus "ask every time" with a page preview.
- **アプリごとに指定**：リンク元のアプリを検索して、アプリごとに開き方を設定できます。設定がないアプリは「標準の開き方」に従います
  **Per-app rules**: search your installed apps and assign an open method to each.
- **重ねて表示（オーバーレイ）**：今使っているアプリの上に、端末の比率に合わせた角丸の窓でページを表示。実行中のアプリを切り替えません。バーは1本で上/下を選べ、周りのぼかしもON/OFFできます
  **Overlay browser**: a rounded, full-height floating window over the current app, with a single bar (top or bottom) and optional background blur.
- **バブル（Web heads）**：リンクをバブルに留めておき、タップでそのままオーバーレイ表示。長押しでチラ見せプレビュー
  **Bubbles**: park links as floating bubbles; tap to open in the overlay, long-press to peek.
- **広告ブロック**：オープンソースのブロックリスト（pgl.yoyo.org の ad server list 等）を使ったドメイン遮断。バーにブロック件数が出て、その場でON/OFFできます
  **Ad blocking**: domain blocking using open-source lists (pgl.yoyo.org ad server list and major ad networks), with a live blocked count in the bar.
- **その他**：記事モード、タブと履歴、デスクトップサイト切替、細かい設定を集約した設定画面
  **And more**: article mode, tabs & history, desktop site toggle, and a dedicated settings screen.

## 権限について / Permissions

| 権限 | 用途 |
|---|---|
| 他のアプリの上に重ねて表示 | オーバーレイ表示とバブルに必要です |
| 通知 | オーバーレイ／バブル表示中の状態通知に使います |
| フォアグラウンドサービス | オーバーレイとバブルを維持するために使います |
| インターネット | ページの読み込みに使います |

| Permission | Why |
|---|---|
| Draw over other apps | Required for the overlay window and bubbles |
| Notifications | Status notifications while overlay / bubbles are active |
| Foreground service | Keeps the overlay and bubbles alive |
| Internet | Loading web pages |

## 注意 / Notes

- リンクが元のアプリの中で開かれてしまう場合は、そのアプリ側の設定で「リンクをアプリ内で開く（アプリ内ブラウザ）」をオフにしてください。オフにすると、リンクがデフォルトブラウザ（Hiraku）に渡るようになります。
  If links keep opening inside the source app, turn off that app's "open links in-app" (in-app browser) setting. Links will then be handed to the default browser (Hiraku).
- アプリ別ルールは、リンク元のアプリが参照元（referrer）を渡してくる場合にのみ適用されます。渡してこないアプリからのリンクは「標準の開き方」で開きます。
  Per-app rules apply only when the source app passes a referrer; otherwise the default method is used.
- 一部のサイトはWebView内でのログインを許可していません（Googleログイン等）。その場合はCustom Tabなど他の開き方を選んでください。
  Some sites (e.g. Google sign-in) refuse to work inside a WebView; use a Custom Tab method for those.
- 個人プロジェクトです。動作は無保証で、自己責任でご利用ください。
  Personal project, provided as-is with no warranty.

## ライセンス / License

GPLv3. Hiraku is a fork of [Lynket](https://github.com/arunkumar9t2/lynket-browser) by Arunkumar (GPLv3); upstream copyright notices are preserved in the source files.
広告ブロックのリストは [pgl.yoyo.org adservers](https://pgl.yoyo.org/adservers/) の公開リストをもとに作成しています。
