package dev.linguacraft.client.voice;

import java.io.ByteArrayOutputStream;

/**
 * Limpa o áudio do microfone antes do reconhecimento: reduz o ruído constante e ajusta o volume.
 * O modelo coreano quase não reconhece nada com volume baixo ou ruído, e estes dois passos compensam isso.
 * Recebe e devolve áudio de 16 bits mono, em pedaços de qualquer tamanho.
 */
public final class AudioCleaner {
	private static final int FRAME = 512;
	private static final int HOP = FRAME / 2;
	private static final int BINS = FRAME / 2 + 1;

	private static final double OVER_SUBTRACTION = 2.0;
	private static final double SPECTRAL_FLOOR = 0.1;
	private static final double POWER_SMOOTHING = 0.8;
	/** Compensa o fato de o mínimo do ruído ficar abaixo da sua média. */
	private static final double MINIMUM_BIAS = 1.5;
	/** Quadros entre as trocas da janela de mínimos, cerca de 1,5 s a 16 kHz. */
	private static final int MINIMUM_WINDOW_FRAMES = 94;
	private static final int NOISE_LEARNING_FRAMES = 12;

	private static final double TARGET_LEVEL = 0.08;
	private static final double MAX_GAIN = 40;
	private static final double LEVEL_ATTACK = 0.5;
	private static final double LEVEL_RELEASE = 0.998;
	private static final double GAIN_SMOOTHING = 0.2;
	/** Abaixo disto, em relação ao ruído restante, o quadro não é considerado fala para medir o volume. */
	private static final double SPEECH_OVER_FLOOR = 3;

	private final boolean denoise;
	private final boolean normalize;

	private final double[] window = new double[FRAME];
	private final double[] pending = new double[FRAME];
	private final double[] overlap = new double[HOP];
	private final double[] smoothedPower = new double[BINS];
	private final double[] minimumCurrent = new double[BINS];
	private final double[] minimumPrevious = new double[BINS];
	private final double[] real = new double[FRAME];
	private final double[] imaginary = new double[FRAME];
	private int pendingSamples = HOP;
	private int frames;
	private int leftoverByte = -1;

	private double level = TARGET_LEVEL;
	private double gain = 1;
	private double floor = Double.MAX_VALUE;

	public AudioCleaner() {
		this(true, true);
	}

	public AudioCleaner(boolean denoise, boolean normalize) {
		this.denoise = denoise;
		this.normalize = normalize;
		for (int i = 0; i < FRAME; i++) {
			// Raiz da janela de Hann: aplicada na entrada e na saída, as janelas sobrepostas somam 1.
			window[i] = Math.sin(Math.PI * (i + 0.5) / FRAME);
		}
	}

	/** Pode devolver mais ou menos bytes do que recebeu, porque trabalha em blocos. */
	public byte[] process(byte[] audio, int length) {
		ByteArrayOutputStream output = new ByteArrayOutputStream(length + HOP * 2);
		for (int i = 0; i < length; i++) {
			if (leftoverByte < 0) {
				leftoverByte = audio[i] & 0xFF;
				continue;
			}
			int sample = (short) (leftoverByte | (audio[i] << 8));
			leftoverByte = -1;
			pending[pendingSamples++] = sample / (double) Short.MAX_VALUE;
			if (pendingSamples == FRAME) {
				processFrame(output);
				System.arraycopy(pending, HOP, pending, 0, HOP);
				pendingSamples = HOP;
			}
		}
		return output.toByteArray();
	}

	private void processFrame(ByteArrayOutputStream output) {
		for (int i = 0; i < FRAME; i++) {
			real[i] = pending[i] * window[i];
			imaginary[i] = 0;
		}
		if (denoise) {
			fft(real, imaginary, false);
			suppressNoise();
			fft(real, imaginary, true);
		}
		double energy = 0;
		for (int i = 0; i < HOP; i++) {
			double value = real[i] * window[i] + overlap[i];
			overlap[i] = real[i + HOP] * window[i + HOP];
			real[i] = value;
			energy += value * value;
		}
		updateGain(Math.sqrt(energy / HOP));
		for (int i = 0; i < HOP; i++) {
			int sample = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, real[i] * gain * Short.MAX_VALUE));
			output.write(sample);
			output.write(sample >> 8);
		}
		frames++;
	}

	private void suppressNoise() {
		boolean swapWindow = frames > 0 && frames % MINIMUM_WINDOW_FRAMES == 0;
		for (int bin = 0; bin < BINS; bin++) {
			double power = real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
			smoothedPower[bin] = frames == 0 ? power : POWER_SMOOTHING * smoothedPower[bin] + (1 - POWER_SMOOTHING) * power;
			if (frames == 0 || swapWindow) {
				minimumPrevious[bin] = frames == 0 ? smoothedPower[bin] : minimumCurrent[bin];
				minimumCurrent[bin] = smoothedPower[bin];
			} else {
				minimumCurrent[bin] = Math.min(minimumCurrent[bin], smoothedPower[bin]);
			}
			double noise = MINIMUM_BIAS * Math.min(minimumCurrent[bin], minimumPrevious[bin]);
			double binGain = 1;
			if (frames >= NOISE_LEARNING_FRAMES && power > 0) {
				binGain = Math.max(SPECTRAL_FLOOR, Math.sqrt(Math.max(0, 1 - OVER_SUBTRACTION * noise / power)));
			}
			real[bin] *= binGain;
			imaginary[bin] *= binGain;
			if (bin > 0 && bin < FRAME / 2) {
				real[FRAME - bin] = real[bin];
				imaginary[FRAME - bin] = -imaginary[bin];
			}
		}
	}

	private void updateGain(double frameLevel) {
		if (!normalize) {
			return;
		}
		floor = Math.min(floor * 1.001, Math.max(frameLevel, 1e-5));
		if (frameLevel > floor * SPEECH_OVER_FLOOR) {
			level = frameLevel > level ? LEVEL_ATTACK * level + (1 - LEVEL_ATTACK) * frameLevel : level * LEVEL_RELEASE;
		}
		double desired = Math.min(MAX_GAIN, TARGET_LEVEL / Math.max(level, 1e-5));
		gain += GAIN_SMOOTHING * (desired - gain);
	}

	/** Transformada rápida de Fourier, com tamanho potência de 2. */
	private static void fft(double[] re, double[] im, boolean inverse) {
		int n = re.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				double swap = re[i];
				re[i] = re[j];
				re[j] = swap;
				swap = im[i];
				im[i] = im[j];
				im[j] = swap;
			}
		}
		for (int size = 2; size <= n; size <<= 1) {
			double angle = 2 * Math.PI / size * (inverse ? 1 : -1);
			double stepRe = Math.cos(angle);
			double stepIm = Math.sin(angle);
			for (int start = 0; start < n; start += size) {
				double currentRe = 1;
				double currentIm = 0;
				for (int k = 0; k < size / 2; k++) {
					int a = start + k;
					int b = a + size / 2;
					double tempRe = re[b] * currentRe - im[b] * currentIm;
					double tempIm = re[b] * currentIm + im[b] * currentRe;
					re[b] = re[a] - tempRe;
					im[b] = im[a] - tempIm;
					re[a] += tempRe;
					im[a] += tempIm;
					double nextRe = currentRe * stepRe - currentIm * stepIm;
					currentIm = currentRe * stepIm + currentIm * stepRe;
					currentRe = nextRe;
				}
			}
		}
		if (inverse) {
			for (int i = 0; i < n; i++) {
				re[i] /= n;
				im[i] /= n;
			}
		}
	}
}
