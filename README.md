---

# 🎧 Audio Frequency Visualizer

This project is a **Java Swing desktop application** that records, loads, visualizes, and analyzes audio signals.
It provides real-time waveform visualization, full waveform rendering, and frequency spectrum analysis using FFT.

---

## 📌 Project Overview

The application allows users to:

* Record audio from a microphone
* Load existing WAV audio files
* Visualize audio waveforms in real time
* Display the complete waveform after recording
* Analyze frequency components using Fast Fourier Transform (FFT)
* Save recorded audio as a WAV file
* Export waveform and frequency graphs as an image

---

## 🧱 Technologies Used

* Java SE
* Java Swing (GUI)
* Java Sound API
* FFT implementation (Cooley–Tukey algorithm)
* BufferedImage for graph export

---

## 🖥️ Application Interface

The interface consists of three main visual components:

* **Live Waveform Panel**
  Displays the real-time waveform during recording

* **Final Waveform Panel**
  Displays the complete waveform after recording or file loading

* **Frequency Spectrum Panel**
  Displays frequency magnitudes calculated using FFT

---

## ⚙️ Features

* Mono audio recording at 44.1 kHz, 16-bit
* Real-time waveform amplification for better visibility
* Automatic trimming to power-of-two sample sizes for FFT
* Automatic price-like normalization for frequency magnitude display
* Multi-panel visualization export as PNG
* WAV file save support

---

## ▶️ How It Works

### Audio Recording

* Uses `TargetDataLine` to capture microphone input
* Audio data is stored in a byte stream
* Live waveform updates during recording

### Waveform Visualization

* Audio samples are converted from byte data to signed 16-bit values
* Samples are mapped to panel dimensions
* Time-domain waveforms are rendered using line drawing

### Frequency Analysis

* Audio data is trimmed to the nearest power of two
* FFT is applied to convert time-domain data to frequency-domain
* Magnitudes are normalized and rendered as vertical bars
* Frequency range displayed up to the Nyquist frequency

---

## 💾 Saving & Exporting

* **Save Audio**
  Exports the current recording as a `.wav` file

* **Save Graph**
  Exports all visual panels (live waveform, full waveform, frequency spectrum) as a single `.png` image

---

## ▶️ Running the Application

### Requirements

* Java JDK 8 or higher
* Microphone access (for recording)

### Compile

```bash
javac Audio_Visualizer.java
```

### Run

```bash
java Audio_Visualizer
```

---

## 📁 File Structure

```
Audio_Visualizer.java
```

All functionality, UI components, and inner visualization panels are contained in a single source file.

---

## 🎯 Project Purpose

This project was developed to demonstrate:

* Audio signal processing basics
* Real-time data visualization
* FFT-based frequency analysis
* Java Swing GUI programming
* Integration of audio capture and graphical rendering

---

## 👤 Author

**Salih Alper Çetin**

---
