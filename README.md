# Bookbinding Studio

**Design a hand-bound book, then take everything you need to the bench.**

Bookbinding Studio is a desktop app for people who make books by hand. Describe the book you want: its paper, size, number of signatures and binding style. The app then gives you a true-scale punching template, an animated step-by-step sewing diagram, the length of thread to cut, the sizes to cut the boards and cover to, and a page-order map for printing your own text block.

It's a single Java file with no dependencies.

![Bookbinding Studio preview of a Coptic-bound notebook](screenshots/preview.png)

---

## Bindings

| Binding | What it is | Good for |
|---|---|---|
| **Pamphlet stitch** (3, 5 or 7 holes) | One signature sewn through its fold and cover | Zines, booklets, slim notebooks: a first binding |
| **Kettle stitch** (case binding) | Signatures linked at head and tail with kettle stitches, then cased in boards and cloth | The classic hardback |
| **Long-stitch** | Signatures sewn straight through a soft cover spine, so the stitching becomes the decoration | Journals, leather or card covers, no glue |
| **Coptic** | An exposed spine of chain stitches joining the signatures directly to the boards | Sketchbooks that need to open completely flat |
| **Japanese stab** (yotsume family, 3–8 holes) | Folded leaves stacked and sewn through the whole block, wrapping the spine, head and tail | Single sheets, photo albums, prints |

---

## Installation

Bookbinding Studio is one file, `BookbindingStudio.java`. You need Java to run it, and nothing else: no installer, no libraries, no internet connection once it's on your computer.

If you've never used Java or a terminal before, don't worry. The steps below go slowly, and you only have to do them once.

### Quick start (if you already have Java 17+)

```bash
java BookbindingStudio.java
```

That's it: Java's source launcher compiles and runs the file in one step.

---

### Step 1: Check whether you already have Java

Open a terminal:

- **macOS:** press **⌘ Space**, type **Terminal** and press Return.
- **Windows:** press the **Windows key**, type **cmd** and press Enter. This opens Command Prompt; PowerShell works too.
- **Linux:** open your Terminal app. On many desktops the shortcut is **Ctrl Alt T**.

Then type:

```bash
java -version
```

and press Enter.

- If you see a version number of **17 or higher** (for example `openjdk version "21.0.5"`), you're ready. Skip to **Step 3**.
- If the number is lower than 17 (for example `1.8.0` or `11.0.2`), or you get `command not found` or `'java' is not recognized`, go to **Step 2**.

> **On macOS** you may see a pop-up saying you need to install Java to use the `java` command. That just means Java isn't installed yet. Close it and follow Step 2.

### Step 2: Install Java

You need a **JDK** (Java Development Kit), version **17 or later**. A JRE ("Java Runtime Environment") is *not* enough. Running a `.java` file directly needs the Java compiler, which only comes with the JDK.

The easiest free choice is **Eclipse Temurin**, from [adoptium.net](https://adoptium.net/). **Version 21** is the current long-term-support release and is a good default.

#### macOS

**Option A: installer (simplest)**

1. Go to [adoptium.net](https://adoptium.net/) and download the **Temurin 21 (LTS)** installer for macOS. The site usually detects your Mac's chip; if it asks, choose **aarch64** for Apple Silicon (M1/M2/M3/M4) or **x64** for an older Intel Mac. To check which you have, open the  menu → **About This Mac**.
2. Open the downloaded `.pkg` file and click through the installer.
3. **Quit Terminal and open it again**, then run `java -version` to check.

**Option B: Homebrew**

If you use [Homebrew](https://brew.sh/):

```bash
brew install --cask temurin
```

#### Windows

**Option A: installer (simplest)**

1. Go to [adoptium.net](https://adoptium.net/) and download the **Temurin 21 (LTS)** `.msi` installer for Windows x64.
2. Run it. On the **Custom Setup** screen, make sure **"Add to PATH"** and **"Set JAVA_HOME variable"** are both set to *Will be installed on local hard drive*. This is what lets you type `java` in Command Prompt.
3. **Close Command Prompt and open a new one**, then run `java -version` to check.

**Option B: winget**

On Windows 10 or 11, in Command Prompt or PowerShell:

```bash
winget install EclipseAdoptium.Temurin.21.JDK
```

#### Linux

Use your distribution's package manager. Remember to install the **JDK** package, not just the runtime:

| Distribution | Command |
|---|---|
| Ubuntu, Debian, Mint | `sudo apt update && sudo apt install openjdk-21-jdk` (or `openjdk-17-jdk` on older releases) |
| Fedora | `sudo dnf install java-21-openjdk-devel` |
| Arch, Manjaro | `sudo pacman -S jdk21-openjdk` |
| openSUSE | `sudo zypper install java-21-openjdk-devel` |

Then check with `java -version`.

> If you have several Java versions installed and the wrong one is used, Ubuntu and Debian let you switch with `sudo update-alternatives --config java`.

### Step 3: Download Bookbinding Studio

Choose **one** of these:

**Option A: just the program file (simplest)**

1. On this repository's page, click **`BookbindingStudio.java`** in the file list.
2. Click the **Download raw file** button (a downward arrow, top right of the file view).
3. Move the file somewhere easy to find, for example a new folder called `BookbindingStudio` in your Documents.

> **Safari users:** Safari sometimes saves the file as `BookbindingStudio.java.txt`. If it does, rename it to exactly `BookbindingStudio.java`. In Finder, you may need to turn on **Finder → Settings → Advanced → Show all filename extensions** to see the `.txt` ending.

**Option B: the whole repository as a ZIP**

Click the green **Code** button → **Download ZIP**, then unzip it. This also gets you the README and screenshots.

**Option C: git**

```bash
git clone https://github.com/RDMachinery/BookbindingStudio.git
```

### Step 4: Run it

In your terminal, go to the folder containing `BookbindingStudio.java` using `cd`. For example, if you put it in a folder called `BookbindingStudio` inside Documents:

- **macOS / Linux:**
  ```bash
  cd ~/Documents/BookbindingStudio
  ```
- **Windows:**
  ```bash
  cd %USERPROFILE%\Documents\BookbindingStudio
  ```

> **Tip:** on macOS you can type `cd ` (with a space) and then drag the folder from Finder into the Terminal window, which fills in the path for you. On Windows, you can type `cmd` into the address bar of a File Explorer window that's showing the folder, to open Command Prompt already in it.

Then start the app:

```bash
java BookbindingStudio.java
```

The window should appear within a few seconds. The first launch is slightly slower because Java compiles the file in memory each time.

### Optional: build a double-clickable app

If you'd rather not use the terminal every time, you can package the program as a `.jar` file once, which you can then double-click like any other app. In the same folder, run:

**macOS / Linux**

```bash
mkdir -p build
javac -d build BookbindingStudio.java
jar --create --file BookbindingStudio.jar --main-class BookbindingStudio -C build .
```

**Windows**

```bash
mkdir build
javac -d build BookbindingStudio.java
jar --create --file BookbindingStudio.jar --main-class BookbindingStudio -C build .
```

You can now double-click **`BookbindingStudio.jar`** to open the app, or run it with `java -jar BookbindingStudio.jar`. You can delete the `build` folder afterwards.

> **macOS:** the first time, macOS may say the file "can't be opened because it is from an unidentified developer". Right-click (or Control-click) the jar, choose **Open**, then click **Open** again in the dialogue. You only need to do this once. If you've just installed Java and double-clicking does nothing, restart your Mac once.
>
> **Windows:** if double-clicking opens the jar in a ZIP program instead, right-click it → **Open with** → **Choose another app** → **Java(TM) Platform SE binary** (or the OpenJDK/Temurin equivalent), and tick *Always use this app*.

---

## The tabs

### Preview
The finished book drawn three ways: the front cover, the spine and the book lying open. Each binding's spine is drawn from its actual sewing path, so Coptic shows its chains and long-stitch shows its columns of stitches. Choose your own cover and thread colours and a title.

### Signatures
A view looking down on the head of the book, showing how the sheets nest into signatures, with page ranges. Below it is a **page-imposition table** saying which page numbers go on which side of every sheet, along with the correct duplex setting ("flip on the short edge"). This is what you need if you're printing your own text block.

![Signatures and imposition table](screenshots/signatures.png)

### Template
A punching guide drawn at **true scale**, ready to print:

- **Signature guide:** fold it on the dashed line, sit it in the open signature and pierce at each mark.
- **Board drilling guide** for Coptic.
- **Hole guide** for stab binding, aligned to the spine edge.
- **Cover spine guide** for long-stitch, with one column of holes per signature.

Every template carries a **50 mm calibration bar**. Measure it before you punch. If it isn't exactly 50 mm, reprint at 100% / Actual size. Templates taller than the printable page are split across several pages with a 10 mm overlap.

![Punching template](screenshots/template.png)

### Sewing
The heart of the app. The spine is drawn face-on with every signature and sewing station, and the thread path builds up one step at a time with a plain-English instruction for each step:

> *Kettle stitch: slip the needle between signatures 2 and 3, behind the thread linking them at station 6, then pass it up through the loop you've just formed. Snug it down.*

- **Solid lines** run outside the spine and **dashed lines** run inside the fold. Loops mark kettle, chain and lock stitches.
- Step with **◀ ▶** or the arrow keys, or press **Play** and set the speed.
- Click any step in the list to jump straight to it.
- **Show whole path** draws the complete sewing at once.
- A running total shows how much thread you've used so far.

![Coptic sewing diagram](screenshots/sewing.png)

Stab binding has its own view, showing the front and back of the book side by side, so you can follow the thread as it passes through the block and wraps the spine.

![Stab binding sewing diagram](screenshots/stab-sewing.png)

### Cover & Boards
Cutting diagrams with dimensions for the binding you've chosen:

| Binding | Diagram |
|---|---|
| Case binding | Book cloth with front board, spine stiffener and back board laid out, joint gaps, turn-ins and mitre cuts |
| Coptic | Two covered boards with mitred corners and the hole positions |
| Long-stitch | The limp cover with fore-edge flaps, fold lines and the spine hole grid |
| Pamphlet and stab | Simple cover sheets with their holes |

A cutting list underneath gives every piece and how many to cut.

![Case binding cutting diagram](screenshots/cover.png)

### Materials
A bench list you can work from:

- the number of sheets of paper
- block thickness
- a grain check
- how much thread to cut, compared with the traditional rule of thumb
- the cover pieces
- the tools you'll need
- the order of work

---

## How it works

### Paper and grain
Each sheet is folded once, parallel to its height, so an A4 sheet held landscape gives an A5 leaf. Paper caliper is estimated from its weight (about 0.1 mm at 80 gsm). Block thickness adds the swell from the thread in each fold.

Paper folds and lies flat best when its grain runs **parallel to the spine**. The app works out which grain your sheet orientation needs and warns you if you've chosen the wrong one.

### Sewing paths
Each binding style generates its sewing as a list of steps. Each step records:

- where the needle is: signature or board, and station
- which side of the paper it ends up on
- what it's doing: passing through, linking to a board, kettle, chain, wrap, tie
- how much thread it uses

One shared renderer animates any step list, so adding a new binding only means writing a new step generator.

The paths are built so that every stitch is sewn exactly once and the thread ends where its tail is, ready to tie off.

### Thread length
The thread length isn't a guess or a single rule of thumb. It's the **sum of every step in the actual sewing path**:

- runs along the fold between stations
- passes through the holes
- moves between signatures
- links, kettle stitches and chains
- wraps around the spine and the head and tail

The app adds 10% working slack and two 8 cm tails to that total. The Materials tab also shows the traditional rule of thumb where one exists, so you can compare the two. For a 96-page A5 kettle-stitch book, the path gives 1.53 m and the rule of thumb gives 1.47 m.

If the thread runs past about 2 m, the app suggests sewing with shorter lengths joined by a weaver's knot.

---

## Files

- **File → Save / Open** stores a book design as a small `.bind` text file (Java properties format).
- **File → Export current tab as PNG** saves whichever view you're looking at, at double resolution.
- **File → Print punching template** prints the template at true scale.

All measurements are entered in millimetres. **Units → Inches** switches how the results are displayed.

---

## Troubleshooting

**`java: command not found`, or `'java' is not recognized as an internal or external command`**
Java isn't installed, or the terminal can't find it. Install a JDK (Step 2), then **close and reopen** the terminal. On Windows, re-run the Temurin installer and make sure **Add to PATH** is selected.

**`error: records are not supported in -source 11`, `sealed classes are not supported`, `UnsupportedClassVersionError` or similar**
Your Java is older than 17. Install Java 17 or later (Step 2). If `java -version` still shows the old version afterwards, the old one is first on your PATH: uninstall it, or on Linux use `update-alternatives`.

**`error: can't find main(String[]) method` or `Error: Could not find or load main class`**
Make sure you're running `java BookbindingStudio.java` (with `.java`) from the folder that contains the file, and that the file is named exactly `BookbindingStudio.java`. Watch for a hidden `.txt` ending from Safari, or a `(1)` added by your browser.

**`error: no compiler available`, or `java.lang.IllegalArgumentException: error: release version not supported`**
You have a JRE rather than a JDK. Install a JDK (on Linux, the `-jdk` or `-devel` package).

**The window is tiny or the text is blurry on a high-resolution Windows screen**
Right-click the Java executable or the jar → **Properties → Compatibility → Change high DPI settings**, and tick **Override high DPI scaling behaviour**, scaling performed by **Application**.

**The printed template's 50 mm bar isn't 50 mm**
In the print dialogue, set scaling to **100%**, **Actual size** or **None**, *not* "Fit to page" or "Shrink to fit". Some PDF viewers rescale by default, so print directly from the app if you can.

**The app starts but a tab looks empty**
Try resizing the window slightly. If a problem persists, please open an issue on GitHub with your operating system, the output of `java -version`, and a screenshot.

---

## A note on accuracy

Thread lengths, spine thickness and cover sizes are **bench estimates**. Paper caliper varies between makers, thread swells differently depending on tension, and the joint gap and board set-back on a case binding are traditional rules of thumb, not physics. Check the numbers against your own materials before cutting good cloth or leather. Cutting a test piece first never hurts.

---

## Possible future additions

- More stab patterns: Kikko (tortoiseshell), Asa-no-ha (hemp leaf) and Koki
- French link stitch and sewing on tapes
- Rounded and backed spines for case binding
- Headbands and a spine-lettering layout

---

## Author

**Mario Gianota**
