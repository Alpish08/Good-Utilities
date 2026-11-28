# Changelog

## 2.9.24 — 2026-11-28
- Landscape for the editors: Color picker, Image blur, Background remover, Icon maker and the PDF editor show the picture (or pages) on the left and their controls card on the right, scrolling if needed, instead of a strip of picture above the card.
- Icon maker in landscape: "Choose logo" sits beside the icon-pack card (it was squashed into a line).

## 2.9.23 — 2026-11-28
Landscape, following Samsung's own apps and the One UI guidelines:
- Pages open with the compact title bar in landscape (no big title, which took over half the screen), and the status bar hides (swipe down to see it).
- Pages keep clear of the camera cutout and a side navigation bar; backgrounds still run edge to edge.
- The fades at the top and bottom of lists are half as long in landscape.
- Bottom buttons stay phone-width and centred instead of stretching across the screen.
- Random, Compressor and Audio trimmer put their result and their controls side by side, so nothing is squeezed under the button.

## 2.9.22 — 2026-11-27
From Samsung's One UI design guidelines:
- Long-press an icon-only button (back, search, ⋮, +, the recents bin…) and its name shows in a small card beside it, like a one-line ⋮ menu; it fades after 1.5 s, and touching anything else goes straight through.
- While selecting, the floating action bar only shows once something is selected; it slides away at zero.
- List titles stay on one line (the summary under them can be longer).

## 2.9.21 — 2026-11-27
- Documents › Recent: a bin beside each document (One UI's, as on the Home screen's Remove) takes it off the list in one tap, with Undo. Long-press to select several, with All and a floating Remove bar. Only the list is cleared; the files stay.
- Card balance's floating Delete uses the same One UI bin.

## 2.9.20 — 2026-11-27
- Home › Background lists the colours by name only, without the colour dots.

## 2.9.19 — 2026-11-27
- About: a Support card with "Buy me a coffee", opening Good Utilities' Ko-fi page.

## 2.9.18 — 2026-11-26
- Tools' explanations moved off their pages into **⋮ › Tips**, a frosted popup, so pages show only what you can tap: Music downloader (How it works), Video downloader (Supported, audio formats), File converter (What can be converted), Documents (Make it your default), App lock, APK extractor, Japanese reader (sharing, recognition, credits), Private DNS, Charge limit, Battery info, Card balance, Virtual cards, Briefs and QR code. Empty states, errors and setup steps stay on the page.

## 2.9.17 — 2026-11-26
- List rows' press highlight is rounded and sits 5 dp inside the row, like the ⋮ menu's items, clear of the card's edges and the dividers.

## 2.9.16 — 2026-11-26
- Every tappable list row in the app (tool pages, About, pickers…) gets Settings' press: the contents ease in to 99.2 % while the highlight fills the row.

## 2.9.15 — 2026-11-26
- Pressing works like Samsung's: the highlight fills the item while its contents ease inwards. Home's tool cards shrink slightly, like Settings' rows (99.2 %), and ⋮ menu items further, like Clock's (96 %).

## 2.9.14 — 2026-11-26
- Popups with side-by-side buttons (e.g. Delete | Cancel | Save): each press highlight stops 6 dp short of the dividers instead of touching them.

## 2.9.13 — 2026-11-25
- The floating action bar's buttons (e.g. Delete while selecting) have their icon and label centred: the gap above the icon now matches the gap under the label.

## 2.9.12 — 2026-11-25
- Reverted 2.9.10 and 2.9.11: the tab bar has its earlier colour, glow and outline back, and the frosted pills blur what's behind them in colour again.

## 2.9.11 — 2026-11-25
- The frosted pills (tab bar, search, selection actions) blur what's behind them in grey, so coloured icons sliding underneath no longer tint them magenta.

## 2.9.10 — 2026-11-25
- Tab bar: no outline, and Gallery's colours over a black page (36 at the ends, 40 in the middle, the highlight 66). The search pills keep their outline; Home's search morph starts in the tab bar's colour and blends into the search bar's.

## 2.9.9 — 2026-11-25
- Card balance: the balance and the entry row share one card, under a divider like a list's rows.
- The Log button is as wide as the − and + pair, so the amount box sits centred between them.

## 2.9.8 — 2026-11-25
- Virtual cards blocks screenshots, screen recording and the recent-apps preview.
- Selecting: once the list scrolls, the floating chip shows the count beside the All check (Reminder's "○ 2").
- Tab bar: icons and labels sit 2 dp lower, so the space above the icons matches the space under the labels.

## 2.9.7 — 2026-11-25
- Virtual cards › Add card: Save sits beside the card name, on its line, so it's in reach without scrolling.

## 2.9.6 — 2026-11-25
- The tab bar is Gallery's height: 58 dp with a 50 dp highlight, 4 dp in all round (it was 2 dp thinner). Home's search morph and the selection action bar match it.
- Virtual cards › Add card: Front | Back switch (like the Compressor's) over one photo slot for the chosen side; the add-photo icon's "+" sits at the picture's top-right corner.

## 2.9.5 — 2026-11-25
- Virtual cards › Add card: the name is typed under the front photo, centred and bold over a short line, just where the cards page shows it.
- The add-photo icon is the Images tab's filled picture with a "+" at its corner.

## 2.9.4 — 2026-11-25
- Virtual cards: under the cards, One UI's page indicator (the current card a capsule), then the card's name and which side is showing, in place of Wallet's panel.
- The photo slots use a One UI-style add-photo icon (the Gallery picture with a "+").

## 2.9.3 — 2026-11-25
- ID cards is now **Virtual cards**.
- Cards turn over the way you swipe: down and the top comes towards you, up and the bottom does. No tap to flip, and no hint text.
- Cards match Samsung Wallet's: 336 × 209 dp, 38 dp from the screen edges, ~12 dp corners, with the name panel 6 dp in from the sides.

## 2.9.2 — 2026-11-25
- ID cards: just the swipeable cards (no All tab or card page). Swipe down on a card (or tap) to turn it over; the ⋮ menu (rename, replace, add or remove the back, delete) acts on the card in view. Cards have a thin grey outline, and switching cards plays Samsung Wallet's own haptic tick.
- Selecting: on the floating chip the "All" circle sits in the chip's exact centre; the floating Delete button's press highlight clears the pill's edge by 4 dp all round.

## 2.9.1 — 2026-11-25
- Selecting: the check pops in (the blue fills out from the centre with a slight bounce, the tick scales in) and each row's text slides over to make room; "All" sits over the rows' checks.
- ID cards: Samsung Wallet's layout. Quick access (one card at a time with its neighbours peeking in, swipe between them, its name on a panel underneath) and All (the stack), switched from a bottom tab bar; the card page has a compact top bar with the card just under it, and an info card.

## 2.9 — 2026-11-25
- New: **ID cards** (Misc). Keep a copy of your IDs' front and back, Samsung Wallet style. It asks for your fingerprint or PIN every time it opens, and closes when the screen turns off or you leave the app. Photos and names are encrypted with a hardware-backed key, stored only in the app, and kept out of backups. Add a card from your photos, or share a photo to "Add to ID cards"; tap or swipe to flip it.
- Multi-select, My Files / Reminder style: long-press a payment in Card balance to select, "All" and Cancel at the top, Delete in a floating bar, with Undo.

## 2.8.18 — 2026-11-24
- Files tab: Compressor's icon is purple and APK extractor's is Android green, so Print, Compressor and APK extractor are no longer three blues in a row.

## 2.8.17 — 2026-11-24
- The ⋮ icon matches Samsung's own (Reminder, Clock): 11 px dots, 21.5 px apart on the S24 Ultra.
- The separate windows are named One Brief (Briefs) and One Reader (Document reader), in recent apps and "Open with".

## 2.8.16 — 2026-11-24
- The ⋮ icon is 15 % smaller, in proportion with the other toolbar icons.

## 2.8.15 — 2026-11-23
- Their own windows are named like their pages: One Mail is now Briefs, One Viewer is now Document Viewer.

## 2.8.14 — 2026-11-23
- Text boxes and unselected buttons inside popups are see-through with just their outline.

## 2.8.13 — 2026-11-23
- Popups are a step darker than their text boxes (#282828 in dark mode), at the same opacity and blur.

## 2.8.12 — 2026-11-23
- Popups are a little more opaque (88 % in dark mode, 90 % in light), closer to their text boxes' grey with the blur still visible.

## 2.8.11 — 2026-11-23
- Popups use their text boxes' grey as their base colour (same see-through alpha and blur as before).

## 2.8.10 — 2026-11-23
- Popups with text boxes rise whole above the keyboard (title, fields and buttons), instead of only the focused box being scrolled into view with the rest of the popup under the keyboard.

## 2.8.9 — 2026-11-23
- The bottom "Search" pill (App lock, APK extractor, song picker) morphs into the full search bar: it widens while rising with the keyboard, and rides back down on top of it into the pill when closing.
- Home: search morphs the tab pill itself into the search bar the same way; the tabs fade out (riding up with it) as the search field fades in, and back again on closing.

## 2.8.8 — 2026-11-22
- Briefs: tapping a pill or link no longer flashes a blue rectangle; pills brighten slightly while pressed.

## 2.8.7 — 2026-11-22
- Briefs: the "Reading aloud · Stop" pill is frosted like the header and the Next pill.

## 2.8.6 — 2026-11-22
- Briefs: the sync button turns once when pressed.
- Briefs: no emoji anywhere: list, header, article title and text, and read-aloud.
- Briefs: the "Next: …" pill at the end of an article has the header's frosted grey and blur.

## 2.8.5 — 2026-11-22
- Briefs: the refresh button shows the same loading animation as pull to refresh.

## 2.8.4 — 2026-11-22
- Briefs: pull down to refresh, with Samsung's four-dot loading animation just below the back and ⋮ buttons, over the first cards.

## 2.8.3 — 2026-11-22
- Tab bar: the selected highlight is centred on its tab to the pixel, with an even 4 dp gap on every side (it used to drift left toward Misc).
- Briefs: the list is grouped into three cards (Today, Yesterday and Past, with dates on past briefs).
- Briefs: the header keeps its dark frosted look over a page shown in light, like the PDF viewer over white pages.

## 2.8.2 — 2026-11-22
- Briefs keeps the 80 newest emails by date. Before, labelling a batch of old newsletters could push recent ones out.
- Briefs list: titles without emoji, aligned left; the unread dot moved to the right.
- Briefs: "Show in light" in the ⋮ menu for newsletters made for a white background.
- Briefs drops more clutter: the newsletter's own copy of the subject, "read in browser", "someone forwarded you this", "subscribe to this newsletter".
- Card balance: expenses in the payment history are red-orange with their minus sign.
- Home: the title stays pinned inside individual tabs; only the all-tools view scrolls it away.
- Japanese reader's icon is pink; Briefs' icon is red.

## 2.8.1 — 2026-11-22
- Samsung's own loading animation (four gradient dots that spin, gather and spread), rebuilt from One UI's timings and curves.
- Briefs moved to Misc, APK extractor to Files. Briefs has no separate launcher icon; it shows as "One Mail" in recent apps.
- Good Reader is now One Viewer.
- Briefs: frosted header like the PDF reader; Contents and Text size moved into the ⋮ menu; the progress bar is gone.
- Briefs fixes: briefs no longer open blank after changing the label; "Show images" works on the first tap; small data tables stay on one line.
- Tabs get the bottom fade like the all-tools view.
- The Change label box matches the other popups' text boxes.

## 2.8 — 2026-11-22
- New: **Briefs**. Your Gmail newsletters and articles from one label as clean, readable One UI pages. It syncs only while it's open, and opening a brief marks it read in Gmail.

## 2.7.12 – 2.7.21 — 2026-11-20 to 21
- New app icon: "drawer alt 01" from OneDark 3D by Eatos.
- Home background colours: Midnight, Amethyst, Lagoon, Moss, Ember, Rosewood and Graphite.
- My Files' floating search pill on App lock, APK extractor and the song picker. It opens a frosted search bar above the keyboard, matching Home's.
- One UI-sized (44 dp) buttons. Link cards are smaller with white labels: paste from the field's icon and press Enter to fetch.
- Card balance: new layout with a quick card switcher, one entry row and a roomier note box. The text now talks about tracking expenses and payments.
- PDF reader: smaller page indicator with the header's frost.
- Popup buttons: a lone button gets a narrow centred press highlight; side-by-side buttons highlight their full width.

## 2.7 – 2.7.11 — 2026-11-20
- The tab bar became toggles (tap a tab for its tools, tap again for all), with a blurred, shadowed tab pill.
- Floating, shadowed toolbar buttons on the all-tools Home.
- Photo compression in the Compressor.
- PDF reader: rotate to landscape and a frosted header.
- The menus' frosted look for floating buttons, popups and the page indicator; white popup buttons.
- App lock locks apps that hide their windows from accessibility (e.g. Authy). Locking the phone on the App lock page backs out of it.
- One UI spinners and progress bars; the title fold snaps correctly again.

## 2.6 — 2026-11-20
- New: **Card balance**, with the 2×4 widget. Battery moved to Device as **Battery info**.

## 2.5 — 2026-11-18
- New: **Tabs widget** (4×1), the app's tab bar on the home screen.

## 2.4 — 2026-11-16
- New: **Icon maker**. Put any logo on your icon pack's empty tile.

## 2.3 — 2026-11-16
- **Auto bypass when hot** (Charge limit): bypass charging turns on at a chosen battery temperature while plugged in, with an optional night skip.
- Ending bypass restores each Battery protection value it changed.

## 2.2 — 2026-11-13 to 15
- Labelled One UI tab bar, tuned to match Clock and My Files.
- Crash log in About. PDF drawing is S Pen only.
- Bypass actions in Modes and Routines return to the app you were in; the Quick Settings tile shows the same toasts.

## 2.1 — 2026-11-13
- Single-page zoom in the PDF reader; create PDFs from images and draw on them.

## 2.0 — 2026-11-13
- One UI 8.5 redesign.
