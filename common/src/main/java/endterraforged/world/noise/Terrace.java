/*
 * Copyright (c) 2023 ReTerraForged
 * Copyright (c) TerraForged
 *
 * Ported from ReTerraForged's Terrace (MIT) into EndTerraForged
 * (LGPL-3.0-or-later). Lineage: TerraForged (dags) -> ReTerraForged
 * (raccoonman) -> EndTerraForged.
 *
 * Pure-algorithm adaptation: drops the upstream codec surface while preserving
 * step construction and per-step ramp/cliff shaping.
 */
package endterraforged.world.noise;

/**
 * Quantizes a source field into softly blended terrace steps.
 *
 * <p>The runtime is immutable and safe to share across world-generation workers.
 */
public record Terrace(Noise input, Noise ramp, Noise cliff, Noise rampHeight,
                      float blendRange, Step[] steps) implements Noise {

    public Terrace(Noise input, Noise ramp, Noise cliff, Noise rampHeight,
                   float blendRange, int steps) {
        this(input, ramp, cliff, rampHeight, blendRange, createSteps(input, blendRange, steps));
    }

    @Override
    public float compute(float x, float z, int seed) {
        float inputValue = Math.clamp(this.input.compute(x, z, seed), 0.0F, 0.999999F);
        int index = NoiseMath.floor(inputValue * this.steps.length);
        Step step = this.steps[index];
        if (index == this.steps.length - 1 || inputValue < step.lowerBound) {
            return step.value;
        }
        if (inputValue > step.upperBound) {
            return this.steps[index + 1].value;
        }

        float rampValue = 1.0F - this.ramp.compute(x, z, seed) * 0.5F;
        float cliffValue = 1.0F - this.cliff.compute(x, z, seed) * 0.5F;
        float alpha = (inputValue - step.lowerBound) / (step.upperBound - step.lowerBound);
        float value = step.value;
        if (alpha > rampValue) {
            Step next = this.steps[index + 1];
            float rampAlpha = (alpha - rampValue) / (1.0F - rampValue);
            value += (next.value - value) * rampAlpha * this.rampHeight.compute(x, z, seed);
        }
        if (alpha > cliffValue) {
            value = NoiseMath.lerp(value, this.steps[index + 1].value,
                    (alpha - cliffValue) / (1.0F - cliffValue));
        }
        return value;
    }

    @Override
    public float minValue() {
        return this.input.minValue();
    }

    @Override
    public float maxValue() {
        return this.input.maxValue();
    }

    @Override
    public Noise mapAll(Visitor visitor) {
        return visitor.apply(new Terrace(
                this.input.mapAll(visitor),
                this.ramp.mapAll(visitor),
                this.cliff.mapAll(visitor),
                this.rampHeight.mapAll(visitor),
                this.blendRange,
                this.steps.length));
    }

    private static Step[] createSteps(Noise input, float blendRange, int count) {
        if (count < 2) {
            throw new IllegalArgumentException("terrace steps must be >= 2, got " + count);
        }
        float spacing = (input.maxValue() - input.minValue()) / (count - 1);
        Step[] result = new Step[count];
        for (int i = 0; i < count; i++) {
            result[i] = Step.create(i * spacing, spacing, blendRange);
        }
        return result;
    }

    /** Immutable precomputed bounds for a single terrace step. */
    public record Step(float value, float lowerBound, float upperBound) {

        private static Step create(float value, float spacing, float blendRange) {
            float blend = spacing * blendRange;
            float bound = (spacing - blend) * 0.5F;
            return new Step(value, value - bound, value + bound);
        }
    }
}
