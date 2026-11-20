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

## What's new in v2.3.7

- **Overlay: the far edge no longer resizes**: following the ColorOS floating-window model, resizing now lives only on the bar's own corners. The far-corner grip and the far edge strip are gone, so reaching for the top of the window no longer starts a resize.
- **Three-dot move handle (ColorOS style)**: the move handle at the center of the far edge is now three dots, like the handle on OPPO's floating windows. Long-press it to grab the window, then drag to move. A plain tap or a short swipe does nothing.
- Includes everything from v2.3.6 and v2.3.5 below.

## What's new in v2.3.6

- **Real liquid glass for the main bottom bar (trial)**: on Android 13+, the main screen's bottom bar renders through QmDeve's AndroidLiquidGlassView (MIT license, real refraction + dispersion), layered under the bar's controls. Older Android versions keep the in-house glass. (Library version 1.0.3 is used: 1.0.4 and later declare minCompileSdk 37, which this module cannot satisfy.)

## What's new in v2.3.5

- **Back to Home — the actual root cause**: the bottom bar's gesture handler compared taps against the tab index captured when the app started (its pointerInput never restarts), so after leaving Home, taps on Home were silently discarded as "already selected" — the pill moved, the screen didn't. The handler now always reads the current tab, and drag-release targets are judged by finger position. This supersedes the v2.3.4 note below, which was a misdiagnosis.
- **White line on bar press — fixed**: the pill's border double-composited into a bright outline, and the velocity squash lacked an absolute value, so moving left swelled the pill vertically until its white edge poked out of the bar. Border removed, single flat fill, squash uses speed magnitude only.
- **Overlay bar grips — fixed**: the bar-corner grips now decide tap vs. drag by movement. A drag resizes (width and height independently, so aspect adjustment works), a tap reaches the close / menu button underneath.

## What's new in v2.3.4

- **Crash when adjusting the overlay window — fixed**: v2.3.3 paused the backdrop blur during drags by removing and re-adding the whole window, and on ColorOS that crashed the service as soon as a resize or move started. Drag-time blur flips now only update the window's layout params in place; the re-attach remains only for passthrough transitions, which happen outside any drag.
- **Menu and close buttons dead on phone-size windows — fixed**: the new bottom corner grips overlapped the bar's corner buttons and swallowed their taps. A grip now only takes touches that start in the outermost 14dp corner sliver; every other tap falls through to the button underneath.
- **"Can't get back to Home" — the real cause, fixed**: after visiting the Apps tab, the bottom bar's pill moved but the screen never switched, so the Home tab could not be reached again (this was misread for three releases as a system home-gesture problem — it is the in-app Home tab). The tab switcher used AnimatedContent, whose transition never completed once the Apps screen kept changing size while its list loaded, wedging every later switch. Tab content is now composed directly per tab with a small enter animation that cannot wedge.

## What's new in v2.3.3

- **Home gesture, audited end to end**: the gesture still failed on device — even while only the main app was in use — so every window Hiraku can have alive was audited. The gesture-free strip at the bottom of the screen is now a 64dp-class exclusion (ColorOS's gesture area is wider than the nominal navigation bar), and it applies uniformly: the overlay's main window (default shape, title drags, corner grips, edge strips, move handle), both tap catchers, the ⋮ menu's bottom edge, and the bubbles' dock position (a bubble being dragged may pass through the strip; where it docks may not). The main app's activities were checked too — no full-screen touch layers or gesture-stealing window flags there.
- **Resize from the bottom corners too**: the bar's own edge had no resize affordance — only the far corner grip and the far edge strips. Both corners of the bar edge now carry corner grips (ColorOS 17 mini-window style): drag a bottom corner and the window resizes with its top edge fixed. The grips are small corner areas, so bar buttons keep their taps, and like the existing corner grip they work even with free resize turned off.
- **The page stays visible while resizing**: the drag-time opaque cover that blacked out the whole window (WebView included) is gone. The backdrop-blur pause is now done by re-attaching the window itself with the blur flag off — the same root view and layout params, only the blur attributes change — so the page never disappears mid-drag.
- **Blur is always off while you use the app underneath**: entering passthrough now takes a blur pause of its own, and exiting restores the blur per your overlay-blur setting. Drag pauses and the passthrough pause are counted together, so the blur only returns once every hold is released.
- **Corner grip arc no longer clipped**: the grip's box was smaller than the arc it draws (48dp box, 56dp arc), so the arc's ends were cut off. The box is now 64dp and the arc sits exactly on the window's real 28dp corner circle, in all four corner orientations.
- **The bubble ✕ target no longer clips when it grows**: the trash window was exactly the circle's size, so the 1.18x hot scale spilled past the window surface. The window now carries padding around a centered inner circle, and the scale applies to the circle only.
- **Chooser preview card joins the glass**: the link preview card was a solid surface color — pitch black in dark mode, with its title nearly unreadable. It now uses the sheet's glass background, with the title and URLs in the glass content colors (the security-check line keeps its status colors).
- **No more white streak on the bottom bar**: pressing a bar item compressed the highlight capsule's bright gradient band into a visible white line. The capsule fill is now near-flat and its border fainter; the motion itself is unchanged.

## What's new in v2.3.2

- **The bottom bar is rebuilt on MeiloX's actual glass implementation**: v2.3.1's bar only looked glass-like — it painted a flat tint and a solid-color highlight. The bar now uses the real values from Glasmio (the glass library MeiloX ships with): the measured iOS container tint (0xFF1C1C1E at 54%), the glass capsule highlight (a translucent white fill with a lit edge instead of a solid color), and the exact spring constants of Glasmio's DampedDragAnimation — value spring (damping 1, stiffness 1000), press lift and scale springs, and its velocity "squash" on the moving highlight, capped at ±0.2 exactly as in Glasmio's GlassBottomBar. (Glasmio's refraction shader pipeline itself needs a newer Kotlin/Compose toolchain than Hiraku's, so the rendering is Hiraku's platform blur + these sourced values.)
- **Bubbles land where they should**: the layout pass no longer hijacks a bubble that is mid-animation (that froze bubbles mid-screen), the stack position is clamped to the display, and every bubble motion — snap-back, re-stacking, pop in/out — now runs on iOS spring curves from Glasmio's LiquidMenuMorphMotion.
- **The ✕ target works, for real this time**: the hit test compared the bubble's window coordinates against a target computed from the full display size — two different coordinate spaces, off by the system bars. It now compares both views' real on-screen positions (getLocationOnScreen) with a 140dp radius, and the red highlight, haptic tick and deletion all follow that one test.
- **Home gesture fixed**: the overlay's full-screen tap catcher covered the bottom gesture strip and ate the home gesture. Overlay catcher windows now stop above the gesture area.
- **Overlay menu, anchored and reliable**: the ⋮ menu now opens above its button and follows it when the window moves (before, it appeared at a fixed screen offset), flips below or clamps so it is never clipped, and the toggle actually closes it — the old outside-touch dismissal raced the button's click and re-opened the menu every time; a dedicated catcher window now takes outside taps instead.
- **Overlay resize, structural fixes**: while dragging, an opaque cover sits over the window so the backdrop blur is visibly paused (attribute tweaks alone didn't take effect on some devices); the corner arc now traces the window's real 28dp corner radius, and the resize strips stop at the bar's measured height.
- **Sourced motion everywhere**: the chooser sheet, its two-edge follow pill, the settings popups, the home tab switch and the bubble ⇄ overlay morphs all run on the same iOS spring specs (Glasmio's sheet spring (1, 300), the indicator spring (1, 631.65), response-based stiffnesses), and the chooser sheet gained a spring exit instead of vanishing.
- **Download app list**: each row in Settings → download app now shows the app's icon on the left.

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

## v2.3.7の新機能

- **オーバーレイ：バーと反対側の辺ではリサイズしないように**：ColorOSのフローティング窓の作法に合わせ、リサイズはバーのある側の角だけに統一しました。反対側の角グリップと辺のストリップは廃止したので、窓の上部をつかんでもリサイズが始まりません
- **3点ドットの移動ハンドル（ColorOS風）**：バーと反対側の辺の中央にある移動ハンドルが、OPPOのフローティング窓と同じ「○○○」になりました。長押しで窓をつかんで、そのままドラッグで位置移動。タップや短いスワイプでは動きません
- v2.3.6とv2.3.5の内容もすべて含みます

## v2.3.6の新機能

- **メイン下バーに本物の液体ガラス（試験導入）**：Android 13以降では、メイン画面の下バーの背後にQmDeveのAndroidLiquidGlassView（MITライセンス、本物の屈折＋分散）を敷くようになりました。それ未満のAndroidでは従来の自前ガラスです（ライブラリは1.0.3を使用：1.0.4以降はminCompileSdk 37を要求するため、このモジュールでは使えません）

## v2.3.5の新機能

- **ホームに戻れない問題を本当の原因で修正**：下バーのジェスチャ処理が、アプリ起動時のタブ（ホーム）とタップを比べ続けていました（pointerInputが再起動されず値を掴んだまま）。ホームを一度離れるとホームへのタップが「選択済み」として捨てられ、ピルだけ動いて画面が切り替わりませんでした。現在タブを常に読むよう直し、ドラッグ後の行き先は指の位置で判定します。※下のv2.3.4の説明は誤診だったので、この版の説明が正しいものです
- **バーを押した時の白い線を修正**：ピルの枠線が二重に合成されて明るい輪郭になっていたのと、速度スキッシュに絶対値が無く、左へ動かすとピルが縦に膨らんで白い縁がバーからはみ出していました。枠線を廃止し単一の色塗りに、スキッシュは速度の大きさだけを使うよう修正
- **オーバーレイ窓のサイズ調整グリップを修正**：バーの角グリップが、動きの量でタップとドラッグを判別するようになりました。ドラッグすればリサイズ（幅・高さ独立＝比率調整も可能）、タップは下の閉じる／メニューボタンに届きます

## v2.3.4の新機能

- **オーバーレイの調節で落ちる問題を修正**：v2.3.3ではドラッグ中のぼかし停止で窓全体を付け直していたため、ColorOSではリサイズや移動を始めた途端にサービスが落ちていました。ドラッグ中のぼかしの切り替えはレイアウトパラメータの更新だけにし、付け直しはドラッグの外で起きるパススルー切り替えの時だけに限定しました
- **スマホサイズで設定ボタン等が押せない問題を修正**：下の角グリップがバーの角のボタン（✕と⋮）に被さってタップを奪っていました。グリップが取るのは角の外側14dpで始まるタッチだけにし、それ以外のタップは下のボタンへ通すようにしました
- **「ホームに戻れない」の本当の原因を修正**：アプリタブを開いたあと、下バーのピルだけが動いて画面が切り替わらず、ホームタブに戻れなくなっていました（3版にわたり端末のホームジェスチャの問題と読み違えていましたが、アプリ内のホームタブの話でした）。タブ切り替えに使っていたAnimatedContentは、アプリ一覧の読み込みで画面サイズが変わり続けるアプリタブが絡むと切り替えが完了しないまま固まるため、タブごとに直接表示＋小さな入場アニメーションに置き換えました

## v2.3.3の新機能

- **ホームジェスチャを全面監査**：実機では本体アプリを使っているだけでもジェスチャが効かなかったため、Hirakuが生きている間に持つ窓をすべて洗い出しました。画面下端のジェスチャ禁止帯は64dp級に広げ（ColorOSのジェスチャ判定域は公称のナビバー高より広いため）、適用先も統一：オーバーレイのメイン窓（デフォルト形状・タイトルドラッグ・角グリップ・辺ストリップ・移動ハンドル）、2種のタップ受け窓、︙メニューの下端、バブルのドック位置（ドラッグ中の通過は可、止まる位置は帯の外）。本体アプリのActivity側も確認済みで、全画面のタッチ層やジェスチャを奪う窓フラグはありませんでした
- **下の角からもリサイズ可能に**：従来のリサイズ導線はバー反対側の角グリップと遠辺ストリップだけで、バー側（下）には何もありませんでした。ColorOS 17の小窓方式で、バー側の左右両角にも角グリップを追加。下の角を引くと上端を固定したままリサイズできます。グリップは角の小領域だけなのでバーのボタンは従来どおり効き、既存の角グリップと同じく自由リサイズ設定OFFでも使えます
- **リサイズ中もページが見えるように**：ドラッグ中に窓全体（WebView含む）を真っ黒に覆っていた不透明カバーを廃止。背景ぼかしの一時停止は「窓自体をぼかしフラグOFFで付け直す」方式に変えました（同じrootビューと同じparamsで、ぼかし属性だけ差し替え）。ドラッグ中にページが消えることはありません
- **下のアプリを操作している間はぼかしを必ずOFF**：パススルー開始でぼかし停止を保持し、終了時にオーバーレイぼかし設定どおり復帰させます。ドラッグによる停止とパススルーによる停止はカウンタで合算管理し、両方が解除されるまで復帰しません
- **角グリップの弧の欠けを修正**：グリップの箱（48dp）より描く弧（56dp）の方が大きく、弧の端が切れていました。箱を64dpに広げ、弧は窓の実際の角丸28dpの円に正確に重なります（4つの角の向きすべて対応）
- **バブルの✕ターゲットの拡大時の欠けを修正**：✕窓が円と同寸だったため、ホット時の1.18倍拡大が窓の表面からはみ出して縁が切れていました。窓に余白を持たせ、円は中央の内側ビューにして拡大は円だけにかけるよう変更
- **選択シートのプレビューカードをガラスに**：リンクプレビューカードは単色のsurfaceで、ダーク時は真っ黒になりタイトルもほぼ読めませんでした。シートと同じガラス背景にし、タイトルとURLはガラス用の文字色を明示（セキュリティチェック行の状態色は維持）
- **下バーの白い筋を解消**：バーの項目を押すと、ハイライトカプセルの明るいグラデーション帯がスカッシュで圧縮されて白い線に見えていました。塗りをほぼ均一にし、縁のアルファも下げました（動き自体は変更なし）

## v2.3.2の新機能

- **下のバーをMeiloXの実物のガラス実装で作り直し**：v2.3.1のバーは「ガラス風」で、平らな色塗りと単色ハイライトでした。今回はMeiloXが採用しているガラスライブラリGlasmioの実物の値を採用：iOS実測のコンテナ色（0xFF1C1C1E、54%）、単色ではないガラスカプセルのハイライト（半透明の白＋光る縁）、そしてGlasmioのDampedDragAnimationのスプリング定数そのもの（値スプリング＝減衰1・硬さ1000、押し込みと拡縮のスプリング、移動ハイライトの速度スキッシュはGlassBottomBarと同じ±0.2上限）。※Glasmioの屈折シェーダー自体はHirakuより新しいKotlin/Composeが必要なため移植できず、描画はHirakuのプラットフォームぼかし＋この実測値の組み合わせです
- **バブルの位置が正常に**：アニメーション中のバブルをレイアウト処理が横取りして画面の途中で固まる問題を修正。スタック位置は画面内にクランプし、スナップバック・並び替え・出入りの動きはすべてGlasmioのLiquidMenuMorphMotion由来のiOSスプリング曲線に
- **✕ターゲットを今度こそ修正**：従来の当たり判定はバブルの窓座標と「画面全体サイズから計算したターゲット位置」という別々の座標空間を比べており、システムバーの分だけずれていました。両ビューの実画面座標（getLocationOnScreen）同士を半径140dpで比較する方式に書き換え、赤変色・振動・削除はすべてこの判定に連動します
- **ホームジェスチャが効かない問題を修正**：オーバーレイの全画面タップ受け窓が下端のジェスチャ領域まで覆い、ホームジェスチャを食っていました。受け窓はジェスチャ領域の手前で止まるようになりました
- **オーバーレイのメニューをボタン連動に**：︙メニューはボタンの真上に出て、窓を動かしてもボタンに追従します（従来は画面の固定位置）。画面外にはみ出す場合は下側への反転とクランプで絶対に切れません。トグルで閉じない問題も修正（従来の「外側タッチで閉じる」がボタンのクリックと競合して開き直していました。専用の受け窓が外側タップを処理します）
- **オーバーレイのリサイズを構造から修正**：ドラッグ中は窓の最前面に不透明カバーを被せるので、背景ぼかしの一時停止が目に見えて効きます（属性変更だけでは効かない端末があったため）。角の弧グリップは窓の実際の角丸28dpをなぞる描画に、リサイズ帯の停止位置はバーの実高から算出
- **全体に仕入れたモーションを適用**：選択シートと二辺追従ピル、設定ポップアップ、ホームのタブ切替、バブル⇄オーバーレイ遷移が、同じiOSスプリング仕様（Glasmioのシートスプリング (1, 300)、インジケータースプリング (1, 631.65)、応答時間ベースの硬さ）で動くようになり、選択シートには消え際のスプリングも追加
- **ダウンロードアプリ一覧にアイコン**：設定のダウンロードアプリ選択で、各行の左側にアプリアイコンが表示されます

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
