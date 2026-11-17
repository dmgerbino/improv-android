# Getting Started with Improv 17 (Android)

Welcome to **Improv 17**, a multidimensional analytical spreadsheet powered by a native Rust **Differential Dataflow** engine (`improv_engine`) and **On-Device AI**. Unlike traditional cell-grid spreadsheets (`A1`, `B2`), Improv models your data by **Categories** (dimensions), **Items** (members), and **Measures** (inputs & formulas)—so pivoting axes never breaks a formula.

---

## Part 1: Learning the Software with the 3D Demo Data

When you launch Improv 17 for the first time, it automatically loads the **3D Financial Cube** (`Time × Product × Region`) persisted in your local Mentat `.db` store.

### 1. Understand the 3D Cube Layout
- **Categories & Items**:
  - `Time`: `Q1`, `Q2`, `Q3`, `Q4`
  - `Product`: `PixelBook`, `PixelPhone`, `PixelTablet`
  - `Region`: `Americas`, `EMEA`, `APAC`
- **Measures**:
  - **Input Measures** (editable white cells): `Price`, `UnitCost`, `Quantity`
  - **Derived Measures** (computed blue `ƒ` cells): `Revenue`, `COGS`, `Margin`

### 2. Navigate & Inspect Semantic Coordinates
1. **Tap any cell** in the grid (or use **Arrow keys** / **Tab** / **Shift+Tab** on a hardware keyboard).
2. Look at the dark **Semantic Coordinate Bar** above the grid—instead of `B4`, it displays the exact multidimensional coordinate, such as:
   `Time:Q1|Product:PixelBook|Region:Americas|Measure:Price`
3. Select an **input cell** (e.g., `Q1 · Price` for `PixelBook`), type a new number into the coordinate bar's value editor (or write with a **stylus**), and tap **Set**. Notice that `Revenue`, `COGS`, and `Margin` recalculate incrementally across the cube.
   > [!NOTE]
   > Derived `ƒ` cells are protected from direct overwriting—to change how they behave, edit their formula instead.

### 3. Pivot Categories to Slice & Dice
- **On Tablet / Chromebook / Desktop Mode**:
  - Drag any Category Tile (`Time`, `Product`, `Region`) between the **ROW TRAY**, **COLUMN TRAY**, and **PAGE TRAY**, or click its **`R` / `C` / `P`** quick-switch pill.
  - Use the top menu bar view presets (**`Time × Product`**, **`Product × Time`**, **`Region × Time`**) to snap between common executive views.
- **On Phone (Portrait)**:
  - Tap **Pivot** in the top bar (or **Pivot Categories** at the bottom) to open the pivot sheet and rearrange axes.
- **Page Slicing**:
  - Whenever a category (like `Region`) sits on the **Page** axis, use the **Page Filter Strip** above the grid (`Americas`, `EMEA`, `APAC`) to slice the 2D table by region.

### 4. Experiment with Natural Language Formulas & What-If Shocks
1. Open the **Formula Pane** (bottom pane on Desktop, or **Formulas** button on Phone).
2. Tap an existing formula (e.g., `Margin = Revenue - COGS`) or type a new one in **Controlled Natural Language** or **Symbolic DSL**:
   - `GrossMarginPct equals (Margin divided by Revenue) times 100`
   - `NetProfit = Margin - 5000`
3. Tap **Evaluate** to compile and compute the new measure across every dimension.
4. Open **Analytics** (Phone) or scroll the **Model Inspector** (Desktop) and tap **`+15% Qty`**, **`+10% Price`**, or **`+12% Cost`** under **What-If Scenario Shocks** to see the entire P&L react in real time. Tap **Reset 3D Model** (or **Reset** on Phone) anytime to restore the original demo data.

---

## Part 2: Starting from a Blank Canvas

When you are ready to build your own analytical model from scratch:

### 1. Create a Blank Workbook
- **Desktop / Tablet**: Click **`Blank Workbook`** in the top menu bar.
- This initializes a clean starter model (`Period × Department` with `P1`, `P2`, `Core`, `Growth`, and an editable `Budget` input measure initialized to `0.0`).

### 2. Design Your Dimensions (Categories & Items)
1. **Add New Categories**:
   - In the **Model Inspector** (Desktop) or **Pivot** bottom sheet (Phone), find **Add Category Dimension**.
   - Enter a **Category Name** (e.g., `Region`) and comma-separated **Items** (e.g., `North, South, EMEA`), then tap **Add Category to Page Tray**.
   - Pivot the new category to **Row (`R`)** or **Column (`C`)** as needed.
2. **Expand Existing Categories with New Items**:
   - Under **Expand Category Items & Measures**, enter the target **Category** (e.g., `Period`), type a **New Item** (e.g., `P3` or `Q1-2027`), and tap **`+Item`**.

### 3. Add Input Measures & Derived Formulas
1. Under **Expand Category Items & Measures**, enter a new **Input Measure** name (e.g., `Actuals` or `Headcount`) and a **Default** numeric value, then tap **`+Measure`**.
2. Populate values directly in the grid using the **Semantic Coordinate Bar**.
3. Write derived formulas in the **Formula Pane** using autocomplete chips:
   - `Forecast = Budget * 1.1`
   - `Variance equals Actuals minus Budget`
   - `TotalBudget = SUM(Budget OVER Period)`

---

## Part 3: Connecting Large Datasets, Building Formulas & Analyzing with Built-In AI

Improv 17 includes a streaming Rust CSV engine (`improv_storage_csv`) and an **On-Device AI Analyst** (`ImprovLocalAiEngine`) to ingest external datasets and uncover insights offline.

### 1. Connect & Ingest External Datasets
You have three ways to bring tabular data into the multidimensional cube:

1. **Headered CSV Import (`improv_storage_csv`)**:
   - Open **CSV Import / Export** in the **Model Inspector** (Desktop) or **Analytics** sheet (Phone).
   - Paste or load headered CSV data where columns `0..N-1` represent **Categories** and the final column represents the **Measure** value:
     ```csv
     Time,Product,Region,TargetQuota
     Q1,PixelBook,Americas,180000
     Q2,PixelPhone,EMEA,350000
     ```
   - Tap **Import CSV to Cube**. Improv automatically creates or reuses matching categories/items and populates the tensor atomically.
   - To export any input or computed derived measure back out with formula-injection protection, select a measure and tap **Export '\<Measure\>' CSV**.

2. **Camera / Document Table OCR Ingestion**:
   - Paste or scan raw pipe-delimited (`|`), tab-delimited, or space-aligned table text into **Scanned OCR Table** (e.g., copied from a PDF, receipt, or ML Kit Document Scanner):
     ```text
     Time | Product | Region | ScannedUnits
     Q1   | PixelBook | Americas | $1,450
     ```
   - Tap **Ingest OCR Table**—the parser strips currency symbols (`$`, `€`, `£`) and commas, normalizes to CSV, and loads the data into the cube.

3. **Android Sensor / Telemetry Cube Stream**:
   - Tap **Stream Sensor Cube** to inject a multidimensional `SensorTelemetry` measure across `Time × Product × Region` for immediate formula modeling.

### 2. Build Multi-Dimensional Formulas
Once your dataset is loaded, build cross-dimensional KPIs in the **Formula Pane**:
- **Symbolic & Excel-compatible functions**: `SUM(...)`, `AVERAGE(...)`, `MIN(...)`, `MAX(...)`, `STDEV(...)`, `PREV(...)`
- **Dimensional qualifiers**: Use `OVER`, `BY`, and `EXCEPT` to aggregate across specific axes (e.g., `ShareOfCategory = Revenue / SUM(Revenue OVER Product)`).
- **Stylus Handwriting**: Tap the formula field with a stylus on Pixel Tablet, Fold, or touchscreen Googlebooks to handwrite formulas directly (`Modifier.handwritingHandler`).

### 3. Analyze with Built-In On-Device AI & Statistical Studio
1. **Live Slice Analytics & Distribution Bars**:
   - Under **SLICE ANALYTICS**, tap any measure chip (`Margin`, `Revenue`, `TargetQuota`, etc.) to inspect live **Sum**, **Mean**, **Median**, **Min**, **Max**, **StdDev**, cell count (`n`), and per-row horizontal distribution bars.
2. **Automated On-Device AI Executive Narrative**:
   - Every time you pivot a category, edit a cell, or select a measure, the **ON-DEVICE AI ANALYST** card computes z-scores across the active slice, flags statistical outliers (e.g., `[Outlier +1.4σ]`), detects distribution skew (`right-skewed` vs. `balanced`), and identifies peak/bottom drivers.
3. **Conversational Prompt $\rightarrow$ Formula Synthesis**:
   - Type a plain-English analytical question in the **Ask On-Device AI** box (or tap a quick chip like **`7% Tariff on COGS`**, **`15% SG&A Overhead`**, **`Gross Margin %`**, or **`Unit Contribution`**):
     - *"Calculate profit margin after a 7% tariff on COGS"* $\rightarrow$ synthesizes and evaluates `TariffAdjustedMargin = Revenue - (COGS * 1.07)`
   - Tap **Synthesize & Evaluate with On-Device AI** to generate the formal Improv DSL and immediately compute it in the Rust engine.
4. **Stylus / Gesture What-If Scrubber & Foldable / Cross-Device Workflows**:
   - Select an input measure under **Stylus / Gesture What-If Scrubber**, drag the **`0.70×`–`1.50×`** slider, and tap **Scrub** to stress-test sensitivity.
   - **Foldable Tabletop Posture**: Half-open a foldable (or tap **Tabletop**) to view the 3D grid + AI insight banner on the angled top screen while controlling pivots and AI prompts on the flat bottom deck.
   - **Android 17 Cross-Device Handoff**: Pick up your active model, category order, selected semantic cell coordinate, scroll position, and draft formula seamlessly between your Pixel phone and Googlebook.
