package com.mcspacewizard.emergentstealth.stealth.sound;

/**
 * A noise as one listener heard it.
 *
 * @param event     the noise
 * @param intensity 0..1: {@code 1 - cost / effectiveLoudness}
 * @param cost      propagation cost in blocks (distance plus muffling)
 */
public record HeardNoise(NoiseEvent event, float intensity, float cost) {
}
