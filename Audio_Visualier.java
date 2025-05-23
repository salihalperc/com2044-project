import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class Audio_Visualizer extends JFrame {
    private JButton recordButton, loadButton, saveAudioButton, saveGraphButton, visualizeButton;
    private JLabel statusLabel;
    private LiveWaveformPanel livePanel;
    private FinalWaveformPanel finalPanel;
    private FrequencyPanel frequencyPanel;

    private ByteArrayOutputStream audioData = new ByteArrayOutputStream();
    private volatile boolean isRecording = false;
    private volatile byte[] liveBuffer;
    private byte[] currentAudioBytes;
    private AudioFormat audioFormat;

    private boolean isFileLoaded = false;

    // Reference to the main frame for error reporting from inner classes
    private final JFrame mainFrame;

    public Audio_Visualizer() {
        setTitle("Audio Frequency Visualizer");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Store reference to this frame for error reporting
        mainFrame = this;

        setupUI();
        setupListeners();

        // Initialize default audio format
        audioFormat = getFormat();
    }

    private double[] computeFFT(byte[] audioBytes) {
        int len = audioBytes.length / 2;
        double[] real = new double[len];
        double[] imag = new double[len];

        ByteBuffer bb = ByteBuffer.wrap(audioBytes);
        for (int i = 0; i < len; i++) {
            real[i] = bb.getShort() / 32768.0;
            imag[i] = 0;
        }

        fft(real, imag);

        double[] mags = new double[len / 2];
        for (int i = 0; i < mags.length; i++) {
            mags[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }

        return mags;
    }

    private byte[] trimToPowerOfTwo(byte[] audioBytes) {
        int len = audioBytes.length / 2;
        int powerOfTwo = 1;
        while (powerOfTwo * 2 <= len) powerOfTwo *= 2;

        ByteBuffer bb = ByteBuffer.wrap(audioBytes);
        ByteBuffer trimmed = ByteBuffer.allocate(powerOfTwo * 2);
        for (int i = 0; i < powerOfTwo; i++) {
            trimmed.putShort(bb.getShort());
        }

        return trimmed.array();
    }

    private void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n == 0) return;
        if ((n & (n - 1)) != 0)
            throw new IllegalArgumentException("Length must be power of 2");

        int logN = Integer.numberOfTrailingZeros(n);
        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - logN);
            if (j > i) {
                double temp = real[i]; real[i] = real[j]; real[j] = temp;
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp;
            }
        }

        for (int size = 2; size <= n; size *= 2) {
            int halfsize = size / 2;
            double phaseStep = -2 * Math.PI / size;
            for (int i = 0; i < n; i += size) {
                for (int j = 0; j < halfsize; j++) {
                    double re = Math.cos(j * phaseStep);
                    double im = Math.sin(j * phaseStep);
                    double tre = re * real[i + j + halfsize] - im * imag[i + j + halfsize];
                    double tim = re * imag[i + j + halfsize] + im * real[i + j + halfsize];

                    real[i + j + halfsize] = real[i + j] - tre;
                    imag[i + j + halfsize] = imag[i + j] - tim;

                    real[i + j] += tre;
                    imag[i + j] += tim;
                }
            }
        }
    }

    private void setupUI() {
        // Top panel (buttons + live graph)
        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel buttonPanel = new JPanel();
        recordButton = new JButton("Record");
        loadButton = new JButton("Load");
        saveAudioButton = new JButton("Save as WAV");
        visualizeButton = new JButton("Visualize");
        saveGraphButton = new JButton("Save Graph");

        buttonPanel.add(recordButton);
        buttonPanel.add(loadButton);
        buttonPanel.add(saveAudioButton);
        buttonPanel.add(visualizeButton);
        buttonPanel.add(saveGraphButton);

        statusLabel = new JLabel("Status: Idle", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));

        livePanel = new LiveWaveformPanel();
        livePanel.setPreferredSize(new Dimension(800, 150));
        livePanel.setBackground(Color.LIGHT_GRAY);

        topPanel.add(buttonPanel, BorderLayout.NORTH);
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(livePanel, BorderLayout.SOUTH);

        // Bottom panel (final visualization)
        finalPanel = new FinalWaveformPanel();
        finalPanel.setBackground(Color.WHITE);

        frequencyPanel = new FrequencyPanel();
        frequencyPanel.setPreferredSize(new Dimension(800, 150));
        frequencyPanel.setBackground(new Color(240, 240, 255));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(finalPanel, BorderLayout.CENTER);
        bottomPanel.add(frequencyPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.CENTER);
    }

    private void setupListeners() {
        recordButton.addActionListener(e -> {
            if (!isRecording) {
                isFileLoaded = false;
                startRecording();
                recordButton.setText("Stop");
                statusLabel.setText("Status: Recording...");
            } else {
                stopRecording();
                recordButton.setText("Record");
                statusLabel.setText("Status: Recorded");
                currentAudioBytes = audioData.toByteArray();
                finalPanel.setAudio(currentAudioBytes);
                finalPanel.repaint();
            }
        });

        loadButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    isFileLoaded = true;
                    audioData.reset();

                    // Try to get the audio format from the file
                    AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file);
                    audioFormat = audioInputStream.getFormat();

                    // Read all bytes from the file
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    audioData.write(fileBytes);
                    currentAudioBytes = fileBytes;
                    statusLabel.setText("Status: File Loaded");
                    livePanel.clear();
                    livePanel.repaint();
                } catch (IOException | UnsupportedAudioFileException ex) {
                    JOptionPane.showMessageDialog(this, "Error loading audio file: " + ex.getMessage(),
                            "File Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });

        saveAudioButton.addActionListener(e -> {
            if (currentAudioBytes == null || currentAudioBytes.length == 0) {
                JOptionPane.showMessageDialog(this, "No audio data to save",
                        "Save Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("recording.wav"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".wav")) {
                    file = new File(file.getAbsolutePath() + ".wav");
                }

                try {
                    saveAsWavFile(file, currentAudioBytes);
                    statusLabel.setText("Status: Saved as WAV file");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error saving WAV file: " + ex.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });

        visualizeButton.addActionListener(e -> {
            if (currentAudioBytes != null) {
                finalPanel.setAudio(currentAudioBytes);
                finalPanel.repaint();

                byte[] trimmedBytes = trimToPowerOfTwo(currentAudioBytes);
                double[] freqs = computeFFT(trimmedBytes);
                frequencyPanel.setFrequencies(freqs, audioFormat.getSampleRate());
                frequencyPanel.repaint();

                statusLabel.setText("Status: Visualized");
            }
        });

        saveGraphButton.addActionListener(e -> {
            // Create a composite image of all three visualizations
            int width = getWidth();
            int totalHeight = livePanel.getHeight() + finalPanel.getHeight() + frequencyPanel.getHeight();
            BufferedImage image = new BufferedImage(width, totalHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();


            livePanel.paint(g2);

            g2.translate(0, livePanel.getHeight());
            finalPanel.paint(g2);

            g2.translate(0, finalPanel.getHeight());
            frequencyPanel.paint(g2);

            g2.dispose();

            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("waveform.png"));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File outputfile = chooser.getSelectedFile();
                    // Ensure the file has .png extension
                    if (!outputfile.getName().toLowerCase().endsWith(".png")) {
                        outputfile = new File(outputfile.getAbsolutePath() + ".png");
                    }
                    javax.imageio.ImageIO.write(image, "png", outputfile);
                    statusLabel.setText("Status: Graph saved");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error saving graph: " + ex.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });
    }

    private void saveAsWavFile(File file, byte[] audioData) throws IOException {
        AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(audioData),
                audioFormat,
                audioData.length / audioFormat.getFrameSize()
        );

        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
    }

    private void startRecording() {
        isRecording = true;
        audioData.reset();

        new Thread(() -> {
            try {
                audioFormat = getFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(audioFormat);
                line.start();

                byte[] buffer = new byte[4096];
                while (isRecording) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        audioData.write(buffer, 0, count);
                        liveBuffer = buffer.clone();
                        livePanel.setAudio(liveBuffer);
                        livePanel.repaint();
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                }

                line.stop();
                line.close();

            } catch (Exception ex) {
                final String errorMsg = ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame,
                            "Recording error: " + errorMsg,
                            "Recording Error", JOptionPane.ERROR_MESSAGE);
                });
                ex.printStackTrace();
            }
        }).start();
    }

    private void stopRecording() {
        isRecording = false;
    }

    private AudioFormat getFormat() {
        return new AudioFormat(44100, 16, 1, true, true);
    }

    // Live waveform panel with axes
    public class LiveWaveformPanel extends JPanel {
        private byte[] audio;
        private final int AXIS_MARGIN = 40;
        private final Font LABEL_FONT = new Font("Arial", Font.PLAIN, 10);
        private final int AMPLIFICATION_FACTOR = 3; // Increase this to amplify the waveform display

        public void setAudio(byte[] audio) {
            this.audio = audio;
        }

        public void clear() {
            this.audio = null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            int w = getWidth() - AXIS_MARGIN;
            int h = getHeight() - AXIS_MARGIN;
            int mid = h / 2;

            g2.setColor(Color.WHITE);
            g2.fillRect(AXIS_MARGIN, 0, w, h);

            // Draw axes
            g2.setColor(Color.BLACK);
            // Y-axis
            g2.drawLine(AXIS_MARGIN, 0, AXIS_MARGIN, h);
            // X-axis
            g2.drawLine(AXIS_MARGIN, h, getWidth(), h);

            // Draw axis labels - moved to prevent overlap
            g2.setFont(LABEL_FONT);

            // Title
            g2.drawString("Live Waveform", w/2, 15);

            // Y-axis labels and ticks - moved to the left
            g2.drawString("Amp", 5, h/2 - 10);
            g2.drawString("+1.0", 15, 10);
            g2.drawString("0", 15, h/2);
            g2.drawString("-1.0", 15, h - 5);

            g2.drawLine(AXIS_MARGIN-5, 0, AXIS_MARGIN, 0);
            g2.drawLine(AXIS_MARGIN-5, h/2, AXIS_MARGIN, h/2);
            g2.drawLine(AXIS_MARGIN-5, h, AXIS_MARGIN, h);

            g2.drawString("Time (samples)", w/2, h + 25);

            if (audio == null || audio.length < 4) return;

            g2.setColor(Color.BLUE);
            g2.setStroke(new BasicStroke(1.5f));

            ByteBuffer bb = ByteBuffer.wrap(audio);
            int samples = Math.min(w, audio.length / 2);

            for (int i = 0; i < samples - 1; i++) {
                short s1 = bb.getShort(i * 2);
                short s2 = bb.getShort((i + 1) * 2);
                int y1 = (int) (mid + (s1 / 32768.0 * mid) * AMPLIFICATION_FACTOR);
                int y2 = (int) (mid + (s2 / 32768.0 * mid) * AMPLIFICATION_FACTOR);

                y1 = Math.max(0, Math.min(h, y1));
                y2 = Math.max(0, Math.min(h, y2));

                g2.drawLine(i + AXIS_MARGIN, y1, i + 1 + AXIS_MARGIN, y2);
            }
        }
    }

    public class FinalWaveformPanel extends JPanel {
        private byte[] audio;
        private final int AXIS_MARGIN = 40;
        private final Font LABEL_FONT = new Font("Arial", Font.PLAIN, 10);

        public void setAudio(byte[] audio) {
            this.audio = audio;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            int w = getWidth() - AXIS_MARGIN;
            int h = getHeight() - AXIS_MARGIN;
            int mid = h / 2;

            g2.setColor(Color.WHITE);
            g2.fillRect(AXIS_MARGIN, 0, w, h);


            g2.setColor(Color.BLACK);
            g2.drawLine(AXIS_MARGIN, 0, AXIS_MARGIN, h);
            g2.drawLine(AXIS_MARGIN, h, getWidth(), h);

            g2.setFont(LABEL_FONT);
            g2.drawString("Complete Waveform", w/2, 15);

            g2.drawString("Amp", 5, h/2 - 10);
            g2.drawString("+1.0", 15, 10);
            g2.drawString("0", 15, h/2);
            g2.drawString("-1.0", 15, h - 5);

            g2.drawLine(AXIS_MARGIN-5, 0, AXIS_MARGIN, 0);
            g2.drawLine(AXIS_MARGIN-5, h/2, AXIS_MARGIN, h/2);
            g2.drawLine(AXIS_MARGIN-5, h, AXIS_MARGIN, h);

            g2.drawString("Time (samples)", w/2, h + 25);

            if (audio != null) {
                int totalSamples = audio.length / 2;
                for (int i = 0; i <= 5; i++) {
                    int xPos = AXIS_MARGIN + (w * i / 5);
                    g2.drawLine(xPos, h, xPos, h + 5);
                    int sampleNum = totalSamples * i / 5;
                    g2.drawString(String.valueOf(sampleNum), xPos - 15, h + 15);
                }
            }

            if (audio == null || audio.length < 4) return;

            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(1.5f));

            ByteBuffer bb = ByteBuffer.wrap(audio);
            int totalSamples = audio.length / 2;
            double scaleX = (double) w / totalSamples;

            int prevX = AXIS_MARGIN;
            int prevY = mid;

            for (int i = 0; i < totalSamples; i++) {
                int x = AXIS_MARGIN + (int)(i * scaleX);
                if (x == prevX) continue;

                short s = bb.getShort(i * 2);
                int y = (int) (mid + s / 32768.0 * mid);

                g2.drawLine(prevX, prevY, x, y);
                prevX = x;
                prevY = y;
            }
        }
    }

    // Frequency panel with axes
    public class FrequencyPanel extends JPanel {
        private double[] frequencies;
        private float sampleRate = 44100; // default
        private final int AXIS_MARGIN = 40;
        private final Font LABEL_FONT = new Font("Arial", Font.PLAIN, 10);

        public void setFrequencies(double[] freqs, float rate) {
            this.frequencies = freqs;
            this.sampleRate = rate;
        }

        public void setFrequencies(double[] freqs) {
            this.frequencies = freqs;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            int w = getWidth() - AXIS_MARGIN;
            int h = getHeight() - AXIS_MARGIN;


            g2.setColor(Color.WHITE);
            g2.fillRect(AXIS_MARGIN, 0, w, h);


            g2.setColor(Color.BLACK);

            g2.drawLine(AXIS_MARGIN, 0, AXIS_MARGIN, h);

            g2.drawLine(AXIS_MARGIN, h, getWidth(), h);


            g2.setFont(LABEL_FONT);

            g2.drawString("Frequency Spectrum", w/2, 15);


            g2.drawString("Mag", 5, h/2 - 10);


            g2.drawLine(AXIS_MARGIN-5, 0, AXIS_MARGIN, 0);
            g2.drawLine(AXIS_MARGIN-5, h/4, AXIS_MARGIN, h/4);
            g2.drawLine(AXIS_MARGIN-5, h/2, AXIS_MARGIN, h/2);
            g2.drawLine(AXIS_MARGIN-5, 3*h/4, AXIS_MARGIN, 3*h/4);
            g2.drawLine(AXIS_MARGIN-5, h, AXIS_MARGIN, h);

            g2.drawString("1.0", 15, 10);
            g2.drawString("0.75", 15, h/4);
            g2.drawString("0.5", 15, h/2);
            g2.drawString("0.25", 15, 3*h/4);
            g2.drawString("0", 15, h - 5);


            g2.drawString("Frequency (Hz)", w/2, h + 25);


            if (frequencies != null) {
                float nyquist = sampleRate / 2;
                // Draw 5 frequency markers
                for (int i = 0; i <= 5; i++) {
                    int xPos = AXIS_MARGIN + (w * i / 5);
                    g2.drawLine(xPos, h, xPos, h + 5);
                    int freqValue = (int)(nyquist * i / 5);
                    g2.drawString(String.valueOf(freqValue), xPos - 15, h + 15);
                }
            }

            if (frequencies == null || frequencies.length == 0) return;

            g2.setColor(Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(1.5f));

            int len = frequencies.length;
            // Calculate how many frequency bins to display (usually up to Nyquist frequency)
            double barWidth = (double) w / len;

            // Find the maximum magnitude for normalization
            double maxMag = 0;
            for (double freq : frequencies) {
                maxMag = Math.max(maxMag, freq);
            }

            // Draw the frequency bars
            for (int i = 0; i < len; i++) {
                double normalizedMag = (maxMag > 0) ? frequencies[i] / maxMag : 0;
                int barHeight = (int) (normalizedMag * h);
                int x = AXIS_MARGIN + (int)(i * barWidth);


                g2.drawLine(x, h, x, h - barHeight);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Audio_Visualizer().setVisible(true));
    }
}
