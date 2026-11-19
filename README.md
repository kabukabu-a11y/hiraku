# Hiraku

**An Android browser that lets you decide how every link opens.**
Choose the open method per source app: your favorite browser's Custom Tab, an overlay window that never leaves the current app, floating bubbles, and more.

Based on [Lynket](https://github.com/arunkumar9t2/lynket-browser) (GPLv3). Hiraku is a personal fork with a redesigned link-opening system, a floating overlay browser, and ad blocking.

## Download

Download the latest APK from [Releases](https://github.com/kabukabu-a11y/hiraku/releases).

After installing, set Hiraku as your default browser so links from other apps are routed to it.

> If Android blocks a permission with "restricted settings", go to Settings → Apps → Hiraku → ︙ (top right) → "Allow restricted settings", then grant the permission again.

## Features

- **9 ways to open a link**: Custom Tab or normal mode of your first/second chosen browser, the system default Custom Tab, an overlay window that stays in the current app, bubbles, share, cancel — plus "ask every time" with a page preview. The chooser items can be reordered and hidden in Settings.
- **Per-app rules**: search your installed apps and assign an open method to each. Apps without a rule follow the standard method.
- **A smarter chooser (v2.1.0)**: the standard method is pinned to the top, the sheet can be collapsed to a compact size, browser methods show the actual browser name, and a checkbox fixes the chosen method for that app on the spot.
- **Overlay browser**: a rounded floating window over the current app, with a single bar (top or bottom) and optional background blur. In v2.1.0 it moves freely, resizes to any size or aspect ratio (geometry is remembered), turns into a bubble from the bar, and has a click-through mode so you can use the app underneath while the page stays visible.
- **Bubbles**: park links as floating bubbles; tap to open in the overlay, long-press to peek. The bubble position is remembered (can be turned off).
- **Ad blocking**: domain blocking using open-source lists (pgl.yoyo.org ad server list and major ad networks), with a live blocked count in the bar.
- **Secret mode (v2.1.0)**: open links without leaving history or tab records — for all links, or only for links from chosen apps.
- **Quick share (v2.1.0)**: pin up to 5 apps as share targets and send links to them in one tap.
- **URL security check (v2.1.0)**: warns before opening suspicious links (IP-address hosts, punycode domains, URLs with embedded user info). Can be turned off.
- **And more**: article mode, tabs & history, desktop site toggle, a dedicated settings screen, and a Japanese / English UI (Settings → Language).

## Permissions

| Permission | Why |
|---|---|
| Draw over other apps | Required for the overlay window and bubbles |
| Notifications | Status notifications while overlay / bubbles are active |
| Foreground service | Keeps the overlay and bubbles alive |
| Internet | Loading web pages |

## Notes

- If links keep opening inside the source app, turn off that app's "open links in-app" (in-app browser) setting. Links will then be handed to the default browser (Hiraku).
- Per-app rules apply only when the source app passes a referrer; otherwise the default method is used.
- Some sites (e.g. Google sign-in) refuse to work inside a WebView; use a Custom Tab method for those.
- Secret mode leaves no history or tab records, but WebView cookies are shared with normal browsing (an Android WebView limitation).
- Personal project, provided as-is with no warranty.

## License

GPLv3. Hiraku is a fork of [Lynket](https://github.com/arunkumar9t2/lynket-browser) by Arunkumar (GPLv3); upstream copyright notices are preserved in the source files.
The ad-blocking list is built from the public [pgl.yoyo.org adservers](https://pgl.yoyo.org/adservers/) list.

---

# Hiraku（日本語）

**「URLの開き方」を自分で決められるAndroidブラウザ。**
リンク元のアプリごとに、どのブラウザで・どう開くかをカスタマイズできます。

[Lynket](https://github.com/arunkumar9t2/lynket-browser)（GPLv3）をベースにした個人フォークです。リンクの開き方を再設計し、フローティング表示のオーバーレイブラウザと広告ブロックを追加しています。

## ダウンロード

[Releases](https://github.com/kabukabu-a11y/hiraku/releases) から最新のAPKをダウンロードしてください。

インストール後、Hirakuをデフォルトブラウザに設定すると、他のアプリのリンクをHirakuが受け取るようになります。

> APKから直接インストールしたアプリは、Androidの「制限付き設定」で一部の許可がブロックされることがあります。その場合は、設定 → アプリ → Hiraku → 右上の︙ →「制限付き設定を許可」を押してから許可し直してください。

## 特徴

- **開き方は9通り＋α**：選んだブラウザのCustom Tab／通常モード、2つ目のブラウザ、システム標準のCustom Tab、アプリに留まる重ねて表示、バブル、共有、キャンセル、そして「開くたびに選ぶ」（選択画面の項目は並べ替え・非表示もできます）
- **アプリごとに指定**：リンク元のアプリを検索して、アプリごとに開き方を設定できます。設定がないアプリは「標準の開き方」に従います
- **選択画面の進化（v2.1.0）**：標準の開き方が一番上に固定され、画面を折りたたんで小さくできます。ブラウザの開き方には実際のブラウザ名が表示され、「このアプリではいつもこれで開く」チェックボックスでその場で固定できます
- **重ねて表示（オーバーレイ）**：今使っているアプリの上に角丸の窓でページを表示。実行中のアプリを切り替えません。バーは1本で上/下を選べ、周りのぼかしもON/OFFできます。v2.1.0では自由に移動・リサイズ（サイズと比率は自由、位置と大きさは記憶）、バーからそのままバブル化、ページを見たまま下のアプリを操作するモードにも対応しました
- **バブル（Web heads）**：リンクをバブルに留めておき、タップでそのままオーバーレイ表示。長押しでチラ見せプレビュー。バブルの位置は記憶されます（OFFにもできます）
- **広告ブロック**：オープンソースのブロックリスト（pgl.yoyo.org の ad server list 等）を使ったドメイン遮断。バーにブロック件数が出て、その場でON/OFFできます
- **シークレットモード（v2.1.0）**：履歴とタブに記録を残さず開けます。全体、または選んだアプリからのリンクだけに適用できます
- **クイック共有（v2.1.0）**：共有先アプリを最大5個まで登録して、1タップでリンクを送れます
- **URLセキュリティチェック（v2.1.0）**：IPアドレスへのリンク、国際化ドメイン（xn--）、ユーザー情報を含むURLなど、怪しいリンクを開く前に警告します（OFFにもできます）
- **その他**：記事モード、タブと履歴、デスクトップサイト切替、細かい設定を集約した設定画面、日本語／英語の表示切替（設定 → 言語）

## 権限について

| 権限 | 用途 |
|---|---|
| 他のアプリの上に重ねて表示 | オーバーレイ表示とバブルに必要です |
| 通知 | オーバーレイ／バブル表示中の状態通知に使います |
| フォアグラウンドサービス | オーバーレイとバブルを維持するために使います |
| インターネット | ページの読み込みに使います |

## 注意

- リンクが元のアプリの中で開かれてしまう場合は、そのアプリ側の設定で「リンクをアプリ内で開く（アプリ内ブラウザ）」をオフにしてください。オフにすると、リンクがデフォルトブラウザ（Hiraku）に渡るようになります。
- アプリ別ルールは、リンク元のアプリが参照元（referrer）を渡してくる場合にのみ適用されます。渡してこないアプリからのリンクは「標準の開き方」で開きます。
- 一部のサイトはWebView内でのログインを許可していません（Googleログイン等）。その場合はCustom Tabなど他の開き方を選んでください。
- シークレットモードでは履歴とタブに記録が残りませんが、CookieはWebViewの仕様上、通常の閲覧と共有されます。
- 個人プロジェクトです。動作は無保証で、自己責任でご利用ください。

## ライセンス

GPLv3。Hirakuは Arunkumar 氏の [Lynket](https://github.com/arunkumar9t2/lynket-browser)（GPLv3）のフォークです。上流の著作権表示はソースファイル内に保持しています。
広告ブロックのリストは [pgl.yoyo.org adservers](https://pgl.yoyo.org/adservers/) の公開リストをもとに作成しています。
