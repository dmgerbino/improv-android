# Improv 17 — Multidimensional Analytical Spreadsheet for Android & Googlebook

A hybrid **Rust + Android 17 (API 37) Jetpack Compose** port of [Greg Burd's Improv (`gburd/improv`)](https://github.com/gburd/improv)—bringing Lotus Improv / Quantrix-style multidimensional modeling, incremental **Differential Dataflow** calculation, and **On-Device AI** to Pixel phones, foldables, tablets, and ARM/Intel Googlebooks.

## Highlights

- **Real Rust Multidimensional Engine ([`rust/improv_android_ffi/`](rust/improv_android_ffi/src/lib.rs))**:
  - Wraps `improv_core_model`, `improv_engine` (`differential-dataflow 0.26`), `improv_nl_formula`, `improv_store` (`mentatdb 1.20` SQLite), `improv_storage_csv`, and `improv_client` via **UniFFI**.
  - Dual-ABI native builds (`arm64-v8a` and `x86_64`) compiled with **16 KB ELF page alignment** (`-z max-page-size=16384`) and SHA-1 build IDs in [`rust/.cargo/config.toml`](rust/.cargo/config.toml).
  - Zero-copy-ready direct `ByteBuffer` viewport transfer (`16B` header + `24B` binary cell stride).
- **Adaptive Material 3 Compose UI**:
  - **Phone Portrait (`WindowWidthSizeClass.COMPACT`)**: Scrollable 2D slice grid + collapsible bottom sheets for Category Tile pivoting, Natural-Language Formulas, and Analytics.
  - **Googlebook / Desktop (`MEDIUM` / `EXPANDED`)**: NeXTSTEP-inspired multi-pane layout with drag-and-drop Category Tile trays, right-side Model Inspector, persistent Formula Pane, hover cursors (`PointerIcon.Hand` / `PointerIcon.Text`), and Arrow/Tab cell navigation.
  - **Foldable Tabletop Posture (`HALF_OPENED`)**: Splits the 3D grid & AI insight banner onto the top angled half and the tactile pivot/AI deck onto the flat bottom half.
- **Android 17 & On-Device AI Capabilities**:
  - **Android 17 Cross-Device Handoff**: `setHandoffEnabled` & `onHandoffActivityDataRequested` packaging active model ID, category order, semantic cell coordinate, scroll indices, and draft formula into a `< 50 KB` `PersistableBundle`.
  - **On-Device AI Analyst ([`ImprovLocalAiEngine.kt`](app/src/main/kotlin/com/droid/improv/ai/ImprovLocalAiEngine.kt))**: Translates conversational prompts into formal Improv DSL and generates Z-score outlier & skew narratives.
  - **System Agent [`ImprovAppFunctionService`](app/src/main/kotlin/com/droid/improv/ai/ImprovAppFunctionService.kt)**: Exposes headless `query_slice_metric`, `run_what_if_simulation`, and `synthesize_and_evaluate_formula` actions.
  - **Stylus & Multimodal Ingestion**: Native `Modifier.handwritingHandler()`, Stylus What-If Scrubber, Camera/OCR table parser, and Sensor Telemetry cube stream.

## Documentation & Visual Guides

- **[Getting Started User Guide](docs/GETTING_STARTED.md)** — Part 1: 3D Demo Data, Part 2: Blank Canvas, Part 3: Large Datasets, Formulas & On-Device AI.
- **[Shareable Mobile Comparison Cards](share_cards/)** — 4 portrait infographics comparing Original Lotus Improv (1991), `gburd/improv` (Rust OSS), and Improv 17 Android.

## Building & Testing

### Prerequisites
- **JDK 17**
- **Android SDK 37** (`compileSdk = 37`, `targetSdk = 37`, `minSdk = 31`) & **NDK**
- **Rust stable** with targets `aarch64-linux-android` & `x86_64-linux-android` and `cargo-ndk`

### Build Debug APK (Compiles Rust for `arm64-v8a` + `x86_64` & Packages APK)
```bash
./gradlew assembleDebug
```

### Run Unit Tests
```bash
# Rust FFI tests
cd rust && cargo test -p improv_android_ffi --lib

# Android ViewModel, Cross-Device Handoff, Analytics & AI tests
./gradlew testDebugUnitTest
```

## License

Dual-licensed under **[Apache-2.0](LICENSE-APACHE) OR [MIT](LICENSE-MIT)**, matching upstream [gburd/improv](https://github.com/gburd/improv).
