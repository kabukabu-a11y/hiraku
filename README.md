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
- **Share targets in the chooser (v2.3.0)**: quick share is gone; apps that accept shared links now appear directly in the chooser as open methods, and you can add or remove them in Settings.
- **URL security check (v2.1.0)**: warns before opening suspicious links (IP-address hosts, punycode domains, URLs with embedded user info). Can be turned off.
- **And more**: article mode, tabs & history, desktop site toggle, a dedicated settings screen, and a Japanese / English UI (Settings → Language).

## What's new in v2.3.1

- **Bottom bar fixes**: taps now switch tabs (only drags worked before), and the liquid highlight keeps its shape while it slides — it stretches just a little while travelling (never more than about 1.3x) and settles with a spring.
- **Stronger glass, with a strength setting**: the default glass is more present than in v2.3.0 (denser tint, brighter edge, stronger backdrop blur in overlay windows), and Settings → Appearance → Glass strength now offers Weak / Medium / Strong. The chooser's follow pill always uses the strongest glass. The glass tint no longer changes color between light and dark mode.
- **Bubbles**: dropping a bubble on the ✕ target actually deletes it now (a bug kept it from firing), the target's hit area is much larger and grows while hot, and bubbles spring back to the edge, slide into place and pop in/out with animations instead of teleporting.
- **Smoother morphs**: the bubble ⇄ overlay transition now sweeps one broad, gentle arc (OPPO-style) in both directions with a ColorOS-style easing curve, and the peek toggle and overlay menu gained matching motion — the ⋮ button now toggles the menu closed with an animation, too.
- **Chooser readability**: text and icons on the chooser sheet use a fixed palette that stays readable on the glass whatever is behind it, the sheet's follow pill got a softer in-between liquid feel, and the settings selection popup's highlight can no longer fly out of its frame.
- **Overlay resize, fixed properly**: the corner arc grip is bigger and outlined so it stays visible on any page, the move handle now has a real touch area, the edge resize strips stop exactly at the bar, and the backdrop blur now genuinely pauses while resizing or moving the window (the window goes opaque during the drag instead of relying on blur flags the device ignored).

## What's new in v2.3.0

- **Liquid Glass everywhere**: the chooser sheet, the resident bottom bar, the overlay menu and the bubbles are all glass (translucent tint + platform backdrop blur on Android 12+). Glass can be turned off in Settings, dynamic color follows your device, and ON toggles glow yellow so state is easy to read.
- **A resident bottom bar**: Home / Apps / History / Settings, always on screen (Settings included), with a highlight that follows your finger as you drag across it. The Apps & Sites screen and the History / Bookmarks tabs are built around it.
- **A rebuilt chooser**: a bottom-attached glass sheet (slight side margins, max width for tablets) whose preview area stays solid; drag the top handle and the sheet expands and collapses with a spring, following your finger; slide across the methods and release to pick; Cancel is red and stays at the bottom; share-target apps sit in the same list as the browser methods.
- **One list to manage the chooser**: long-press drag to reorder with animation; items below the divider are hidden ("2nd browser (normal)" and "System (Custom Tab)" start hidden, newly added apps start hidden too).
- **Settings, reworked**: categorized sections, glass selection popups (dark glass, blue checkmark, a pill highlight that slides to your pick) instead of segmented controls, a note explaining that secret mode still leaves history in the browser you open with, a chooser for the app that handles download links, and backup / restore that now covers *everything* — history, read-later, per-app settings (including secret), site rules and all preferences.
- **A two-row overlay bar**: site icon + title + URL on top (drag it to move the window), seven drawn vector icons below (close / bubble / back / ad-block with counter / reload / expand / menu), all one size and stroke. The ⋮ menu is a glass panel that pops out of the bar, with an icon row (share / bookmark / history / settings) at the bottom. The move handle is now a small translucent pill over the page, resize grips are small arcs outside the frame, and the passthrough "back" handle is glass too.
- **Site-colored bubbles**: the whole bubble is glass tinted with the site's color (from its favicon), only the center letter contrasts, and a small favicon badge sits at the bottom-right. Bubbles fly into the overlay along a curve (and shrink back the same way), and the drag-to-dismiss target is glass that turns red with a haptic tick when the bubble hovers over it.

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
- **共有先アプリを選択画面に（v2.3.0）**：クイック共有は廃止し、共有を受け付けるアプリが選択画面に「開き方」として直接並ぶようになりました（設定で追加・削除できます）
- **URLセキュリティチェック（v2.1.0）**：IPアドレスへのリンク、国際化ドメイン（xn--）、ユーザー情報を含むURLなど、怪しいリンクを開く前に警告します（OFFにもできます）
- **その他**：記事モード、タブと履歴、デスクトップサイト切替、細かい設定を集約した設定画面、日本語／英語の表示切替（設定 → 言語）

## v2.3.1の新機能

- **下のバーを修正**：タップでもタブが切り替わるようになりました（従来はドラッグのみ）。液体ハイライトは形を保ったままスライドし、移動中も少しだけ（最大でも約1.3倍まで）伸びてバネで収まります
- **ガラスを強化＋強さ設定を追加**：デフォルトのガラスは v2.3.0 よりはっきり強く（色の濃さ・縁の光・オーバーレイ窓の背景ぼかしを強化）なり、設定 → 外観 →「ガラスの強さ」で弱／中／強を選べます。選択シートの追従ピルは常に最強のガラスで描画。ガラスの色はライト／ダークモードで変わらなくなりました
- **バブル修正**：✕ターゲットに重ねても消えなかったバグを修正し、判定を大きく＋重ねるとターゲットが拡大するように。端へのスナップバック、並び替え、出入りにもアニメーションが付きました
- **遷移を滑らかに**：バブル⇄オーバーレイの遷移はOPPO風の緩やかな大きな弧を描くようになり（往路・復路とも）、ColorOS系のイージングを採用。ピーク切替とオーバーレイのメニューにも動きが付き、⋮ボタンは再押しでアニメーション付きで閉じるトグル動作になりました
- **選択シートの可読性**：シート上の文字・アイコンは背景に左右されない固定色になり、追従ピルは中間の柔らかさの液体感に。設定の選択ポップアップのハイライトが枠から飛び出さなくなりました
- **オーバーレイのリサイズを本修正**：角の弧グリップを大きく＋縁取りでどのページでも見えるように、移動ハンドルに実用的なタッチ領域を持たせ、端のリサイズ帯はバーの高さちょうどで止まるように。そしてリサイズ・移動中の背景ぼかし一時停止が今度こそ効きます（端末が無視していたぼかしフラグ頼みをやめ、ドラッグ中は窓自体を不透明化）

## v2.3.0の新機能

- **全体がLiquid Glassに**：選択シート・常駐の下バー・オーバーレイのメニュー・バブルがすべてガラス質感（半透明＋Android 12以降は背景ぼかし）に。設定でガラス効果をOFFにもでき、ダイナミックカラーは端末の色に追従。ON状態のトグルは黄色く光って状態が一目で分かります
- **常駐する下バー**：ホーム／アプリ／履歴／設定の4項目が全画面（設定画面含む）に常駐し、指でなぞるとハイライトが追従して切り替わります。「アプリとサイト」画面と履歴／ブックマークのタブ切替もこのバーを中心に再構成
- **選択画面を再構築**：画面下に直接くっついたガラスシート（横に少し余白、タブレット向けに幅上限あり）で、プレビュー部分だけ非ガラス。一番上のハンドルをドラッグするとバネで拡縮し、項目をなぞって離すと選択。キャンセルは赤文字で常時最下部。共有先アプリもブラウザと同じリストに並びます
- **選択項目の管理を1画面に**：長押しドラッグでアニメーション付き並べ替え、区切り線より下は非表示（「第2ブラウザ（通常）」「システム標準」は初期状態で非表示、新規追加アプリも非表示側から）
- **設定を刷新**：カテゴリ分け、セグメント式を廃止してガラス選択ポップアップ（ダークガラス＋青チェック＋追従するピル型ハイライト）に統一、シークレットの「選んだブラウザ側の履歴には残る」旨の説明、ダウンロード用アプリの選択、そしてバックアップ／復元が全データ対象に（履歴・あとで読む・アプリ別設定（シークレット含む）・サイト別ルール・全設定）
- **オーバーレイの下バーが2段構成に**：上段はサイトアイコン＋タイトル＋URL（ドラッグで窓を移動）、下段は描画アイコン7個（閉じる／バブル化／戻る／広告ブロック（件数付き）／リロード／拡大／メニュー）をサイズ・線幅統一で均等配置。⋮メニューはバーから出てくるガラスパネルで、下部に丸アイコン列（共有／ブックマーク／履歴／設定）。移動ハンドルはサイトに被さる小さな半透明ピル、リサイズの角グリップは枠の外側の小さな弧、「下のアプリを操作」中の戻るハンドルもガラス化
- **バブルがサイト色のガラスに**：全体がサイトの色（ファビコンから取得）のガラス、中央の文字だけ別色、右下に小さなファビコンバッジ。バブル⇄オーバーレイはカーブを描いて拡大・縮小し、ドラッグ時の✕受けはガラスで、重ねると赤くなって振動します

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
