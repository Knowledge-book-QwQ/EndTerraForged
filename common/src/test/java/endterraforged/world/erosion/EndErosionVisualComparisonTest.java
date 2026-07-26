package endterraforged.world.erosion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class EndErosionVisualComparisonTest {

    private static final String OUTPUT_PROPERTY = "endterraforged.erosionVisualOutput";
    private static final String OUTPUT_NAME = "p4-7-erosion-candidate-comparison.png";
    private static final long WORLD_SEED = 123456789L;
    private static final long OWNER = 0x13579BDF2468ACE0L;
    private static final int CORE_BLOCKS = 256;
    private static final int SAMPLE_DISTANCE_BLOCKS = 4;
    private static final int PANEL_SIZE = 192;
    private static final int LEFT_MARGIN = 190;
    private static final int TOP_MARGIN = 92;
    private static final int OUTER_MARGIN = 20;
    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 28;
    private static final float DELTA_SCALE_BLOCKS = 12.0F;

    private static final ErosionWorldFixtureBuilder.Kind[] FIXTURES = {
            ErosionWorldFixtureBuilder.Kind.FLOW_FIELD,
            ErosionWorldFixtureBuilder.Kind.WATERSHED,
            ErosionWorldFixtureBuilder.Kind.CLOSED_BASIN,
            ErosionWorldFixtureBuilder.Kind.RIDGE,
            ErosionWorldFixtureBuilder.Kind.COAST_THIN_SHELF,
            ErosionWorldFixtureBuilder.Kind.ARCHIPELAGO
    };

    private static final String[] COLUMNS = {
            "Source relief",
            "Analytical + gate",
            "Bounded thermal",
            "RTF hydraulic",
            "Bounded flow",
            "Analytical drainage",
            "Hydraulic drainage",
            "Flow drainage"
    };

    @Test
    void writesDeterministicSameFixtureCandidateComparison() throws IOException {
        BufferedImage first = renderComparison();
        BufferedImage second = renderComparison();
        assertArrayEquals(rgb(first), rgb(second));

        Path output = outputPath();
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        assertTrue(ImageIO.write(first, "png", output.toFile()));
        BufferedImage decoded = ImageIO.read(output.toFile());
        assertNotNull(decoded);
        assertEquals(first.getWidth(), decoded.getWidth());
        assertEquals(first.getHeight(), decoded.getHeight());
        assertTrue(Files.size(output) > 0L);
        System.out.println("[visual] p47 erosion comparison: " + output.toAbsolutePath());
    }

    private static BufferedImage renderComparison() {
        int width = OUTER_MARGIN + LEFT_MARGIN + COLUMNS.length * PANEL_SIZE
                + (COLUMNS.length - 1) * COLUMN_GAP + OUTER_MARGIN;
        int height = TOP_MARGIN + FIXTURES.length * PANEL_SIZE
                + (FIXTURES.length - 1) * ROW_GAP + OUTER_MARGIN;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        configureGraphics(graphics);
        graphics.setColor(new Color(17, 20, 22));
        graphics.fillRect(0, 0, width, height);
        drawHeader(graphics);

        for (int row = 0; row < FIXTURES.length; row++) {
            int panelY = TOP_MARGIN + row * (PANEL_SIZE + ROW_GAP);
            CandidateSet candidates = buildCandidates(FIXTURES[row]);
            drawRowLabel(graphics, FIXTURES[row], panelY);
            drawCandidateRow(image, graphics, candidates, panelY);
        }
        graphics.dispose();
        return image;
    }

    private static void drawCandidateRow(BufferedImage image,
                                         Graphics2D graphics,
                                         CandidateSet candidates,
                                         int panelY) {
        EndErosionTile input = candidates.input();
        EndErosionTileKey key = input.key();
        float[] range = coreRange(key, input::sourceTopBlocks);
        int panelX = OUTER_MARGIN + LEFT_MARGIN;

        drawReliefPanel(image, key, panelX, panelY, range,
                input::sourceTopBlocks, index -> 0.0F);
        panelX += PANEL_SIZE + COLUMN_GAP;
        drawReliefPanel(image, key, panelX, panelY, range,
                index -> candidates.analyticalTopBlocks()[index],
                index -> candidates.analyticalDeltaBlocks()[index]);
        panelX += PANEL_SIZE + COLUMN_GAP;
        drawReliefPanel(image, key, panelX, panelY, range,
                index -> candidates.thermalTopBlocks()[index],
                index -> candidates.thermalDeltaBlocks()[index]);
        panelX += PANEL_SIZE + COLUMN_GAP;
        drawReliefPanel(image, key, panelX, panelY, range,
                candidates.hydraulic()::finalTopBlocks,
                candidates.hydraulic()::deltaBlocks);
        panelX += PANEL_SIZE + COLUMN_GAP;
        drawReliefPanel(image, key, panelX, panelY, range,
                candidates.flow()::finalTopBlocks,
                candidates.flow()::deltaBlocks);
        panelX += PANEL_SIZE + COLUMN_GAP;
        drawScalarPanel(image, key, panelX, panelY,
                index -> candidates.analyticalDrainagePotential()[index]);
        panelX += PANEL_SIZE + COLUMN_GAP;
        drawScalarPanel(image, key, panelX, panelY,
                candidates.hydraulic()::drainagePotential);
        panelX += PANEL_SIZE + COLUMN_GAP;
        drawScalarPanel(image, key, panelX, panelY,
                candidates.flow()::drainagePotential);

        graphics.setColor(new Color(76, 84, 88));
        graphics.drawRect(OUTER_MARGIN + LEFT_MARGIN - 1, panelY - 1,
                COLUMNS.length * PANEL_SIZE + (COLUMNS.length - 1) * COLUMN_GAP + 1,
                PANEL_SIZE + 1);
    }

    private static CandidateSet buildCandidates(ErosionWorldFixtureBuilder.Kind kind) {
        EndErosionTileKey flowKey = key(
                EndBoundedFlowErosionRuntime.ALGORITHM_ID,
                EndBoundedFlowErosionRuntime.ALGORITHM_VERSION);
        EndErosionTileKey hydraulicKey = key(
                EndHydraulicErosionRuntime.ALGORITHM_ID,
                EndHydraulicErosionRuntime.ALGORITHM_VERSION);
        ErosionWorldFixtureBuilder fixture = new ErosionWorldFixtureBuilder(kind, 128.0F, 128.0F);
        EndErosionTile flowInput = fixture.build(flowKey).tile();
        EndErosionTile hydraulicInput = fixture.build(hydraulicKey).tile();
        assertEquivalentInputs(flowInput, hydraulicInput);

        EndHydraulicErosionRuntime hydraulicRuntime = new EndHydraulicErosionRuntime();
        EndHydraulicErosionTile hydraulic = new EndHydraulicErosionTileBuilder(
                ignored -> new EndErosionTileCache.BuildResult<>(
                        hydraulicInput, hydraulicInput.primitiveBytes()),
                hydraulicRuntime, hydraulicKey.cellCount()).build(hydraulicKey).tile();
        EndBoundedFlowErosionRuntime flowRuntime = new EndBoundedFlowErosionRuntime();
        EndBoundedFlowErosionTile flow = new EndBoundedFlowErosionTileBuilder(
                ignored -> new EndErosionTileCache.BuildResult<>(
                        flowInput, flowInput.primitiveBytes()),
                flowRuntime, flowKey.cellCount()).build(flowKey).tile();

        AnalyticalResult analytical = analytical(flowInput);
        ThermalResult thermal = thermal(flowInput);
        return new CandidateSet(flowInput, analytical.topBlocks(), analytical.deltaBlocks(),
                analytical.drainagePotential(), thermal.topBlocks(), thermal.deltaBlocks(),
                hydraulic, flow);
    }

    private static AnalyticalResult analytical(EndErosionTile input) {
        int cells = input.key().cellCount();
        float[] topBlocks = new float[cells];
        float[] deltaBlocks = new float[cells];
        float[] drainagePotential = new float[cells];
        EndAnalyticalErosionRuntime runtime = new EndAnalyticalErosionRuntime();
        EndAnalyticalErosionBuffer output = new EndAnalyticalErosionBuffer();
        float worldHeight = input.key().worldHeight();
        for (int index = 0; index < cells; index++) {
            float sourceBlocks = input.sourceTopBlocks(index);
            float source = sourceBlocks / worldHeight;
            if (input.erosionProtected(index) || input.areaFamily(index) == 0) {
                output.set(source, 0.0F, 0.0F, 0.0F, 0.0F);
            } else {
                runtime.apply(source, worldHeight, slope(input, index), curvature(input, index),
                        input.roughness(index), input.erosionResistance(index),
                        input.landness(index), input.inlandness(index),
                        input.outerActivation(index), input.availableThicknessBlocks(index),
                        input.archipelagoDominant(index), output);
            }
            topBlocks[index] = output.top() * worldHeight;
            deltaBlocks[index] = output.erosionDelta() * worldHeight;
            drainagePotential[index] = output.drainagePotential();
        }
        return new AnalyticalResult(topBlocks, deltaBlocks, drainagePotential);
    }

    private static ThermalResult thermal(EndErosionTile input) {
        EndErosionTileKey key = input.key();
        int cells = key.cellCount();
        float[] sourceTop = new float[cells];
        float[] landness = new float[cells];
        float[] inlandness = new float[cells];
        float[] outerActivation = new float[cells];
        float[] erosionResistance = new float[cells];
        float[] availableThickness = new float[cells];
        boolean[] erosionMasked = new boolean[cells];
        boolean[] archipelagoDominant = new boolean[cells];
        for (int index = 0; index < cells; index++) {
            sourceTop[index] = input.sourceTopBlocks(index) / key.worldHeight();
            landness[index] = input.landness(index);
            inlandness[index] = input.inlandness(index);
            outerActivation[index] = input.outerActivation(index);
            erosionResistance[index] = input.erosionResistance(index);
            availableThickness[index] = input.availableThicknessBlocks(index);
            erosionMasked[index] = input.erosionProtected(index) || input.areaFamily(index) == 0;
            archipelagoDominant[index] = input.archipelagoDominant(index);
        }

        EndBoundedThermalBuffer output = new EndBoundedThermalBuffer(cells);
        new EndBoundedThermalRuntime().apply(
                key.sampleWidth(), key.sampleHeight(), key.worldHeight(),
                key.sampleDistanceBlocks(), sourceTop, landness, inlandness,
                outerActivation, erosionResistance, availableThickness,
                erosionMasked, archipelagoDominant, output);
        float[] topBlocks = new float[cells];
        float[] deltaBlocks = new float[cells];
        for (int index = 0; index < cells; index++) {
            topBlocks[index] = output.top(index) * key.worldHeight();
            deltaBlocks[index] = output.erosionDelta(index) * key.worldHeight();
        }
        return new ThermalResult(topBlocks, deltaBlocks);
    }

    private static float slope(EndErosionTile input, int index) {
        EndErosionTileKey key = input.key();
        int x = index % key.sampleWidth();
        int z = index / key.sampleWidth();
        int west = z * key.sampleWidth() + Math.max(0, x - 1);
        int east = z * key.sampleWidth() + Math.min(key.sampleWidth() - 1, x + 1);
        int north = Math.max(0, z - 1) * key.sampleWidth() + x;
        int south = Math.min(key.sampleHeight() - 1, z + 1) * key.sampleWidth() + x;
        float dx = (input.sourceTopBlocks(east) - input.sourceTopBlocks(west))
                / (2.0F * key.sampleDistanceBlocks());
        float dz = (input.sourceTopBlocks(south) - input.sourceTopBlocks(north))
                / (2.0F * key.sampleDistanceBlocks());
        float gradient = (float) Math.sqrt(dx * dx + dz * dz);
        return gradient / (1.0F + gradient);
    }

    private static float curvature(EndErosionTile input, int index) {
        EndErosionTileKey key = input.key();
        int x = index % key.sampleWidth();
        int z = index / key.sampleWidth();
        int west = z * key.sampleWidth() + Math.max(0, x - 1);
        int east = z * key.sampleWidth() + Math.min(key.sampleWidth() - 1, x + 1);
        int north = Math.max(0, z - 1) * key.sampleWidth() + x;
        int south = Math.min(key.sampleHeight() - 1, z + 1) * key.sampleWidth() + x;
        float laplacian = (input.sourceTopBlocks(east) + input.sourceTopBlocks(west)
                + input.sourceTopBlocks(north) + input.sourceTopBlocks(south)
                - 4.0F * input.sourceTopBlocks(index))
                / (key.sampleDistanceBlocks() * key.sampleDistanceBlocks());
        return laplacian / (1.0F + Math.abs(laplacian));
    }

    private static void assertEquivalentInputs(EndErosionTile left, EndErosionTile right) {
        assertEquals(left.key().cellCount(), right.key().cellCount());
        for (int index = 0; index < left.key().cellCount(); index++) {
            assertEquals(Float.floatToIntBits(left.sourceTopBlocks(index)),
                    Float.floatToIntBits(right.sourceTopBlocks(index)));
            assertEquals(Float.floatToIntBits(left.landness(index)),
                    Float.floatToIntBits(right.landness(index)));
            assertEquals(Float.floatToIntBits(left.inlandness(index)),
                    Float.floatToIntBits(right.inlandness(index)));
            assertEquals(Float.floatToIntBits(left.outerActivation(index)),
                    Float.floatToIntBits(right.outerActivation(index)));
            assertEquals(Float.floatToIntBits(left.roughness(index)),
                    Float.floatToIntBits(right.roughness(index)));
            assertEquals(Float.floatToIntBits(left.erosionResistance(index)),
                    Float.floatToIntBits(right.erosionResistance(index)));
            assertEquals(Float.floatToIntBits(left.availableThicknessBlocks(index)),
                    Float.floatToIntBits(right.availableThicknessBlocks(index)));
            assertEquals(Float.floatToIntBits(left.ridgeInfluence(index)),
                    Float.floatToIntBits(right.ridgeInfluence(index)));
            assertEquals(left.areaFamily(index), right.areaFamily(index));
            assertEquals(left.terrainTags(index), right.terrainTags(index));
            assertEquals(left.masks(index), right.masks(index));
        }
    }

    private static EndErosionTileKey key(long algorithmId, int algorithmVersion) {
        int coreSamples = CORE_BLOCKS / SAMPLE_DISTANCE_BLOCKS;
        int sampleSize = coreSamples + EndBoundedFlowErosionRuntime.REQUIRED_HALO_SAMPLES * 2;
        return new EndErosionTileKey(
                algorithmId, algorithmVersion, WORLD_SEED, OWNER,
                -256, (int) ErosionFixture.WORLD_HEIGHT_BLOCKS, 4,
                0, 0, sampleSize, sampleSize,
                EndBoundedFlowErosionRuntime.REQUIRED_HALO_SAMPLES,
                SAMPLE_DISTANCE_BLOCKS);
    }

    private static void drawHeader(Graphics2D graphics) {
        graphics.setColor(new Color(232, 235, 232));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        graphics.drawString("P4.7 erosion candidate comparison", OUTER_MARGIN, 30);
        graphics.setColor(new Color(159, 169, 170));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        graphics.drawString(
                "Synthetic fixture only | 256-block core | 4 blocks/sample | fixed +/-12-block delta overlay",
                OUTER_MARGIN, 53);
        graphics.drawString(
                "Analytical panel applies the canonical protection mask before the local runtime.",
                OUTER_MARGIN, 72);

        graphics.setColor(new Color(218, 223, 220));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (int column = 0; column < COLUMNS.length; column++) {
            int panelX = OUTER_MARGIN + LEFT_MARGIN + column * (PANEL_SIZE + COLUMN_GAP);
            int textWidth = graphics.getFontMetrics().stringWidth(COLUMNS[column]);
            graphics.drawString(COLUMNS[column], panelX + (PANEL_SIZE - textWidth) / 2, TOP_MARGIN - 8);
        }
    }

    private static void drawRowLabel(Graphics2D graphics,
                                     ErosionWorldFixtureBuilder.Kind kind,
                                     int panelY) {
        String label = kind.name().replace('_', ' ');
        graphics.setColor(new Color(221, 225, 222));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        graphics.drawString(label, OUTER_MARGIN, panelY + 24);
        graphics.setColor(new Color(122, 133, 135));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.drawString("world seed 123456789", OUTER_MARGIN, panelY + 45);
    }

    private static void drawReliefPanel(BufferedImage image,
                                        EndErosionTileKey key,
                                        int panelX,
                                        int panelY,
                                        float[] range,
                                        FloatGrid top,
                                        FloatGrid delta) {
        drawCore(image, key, panelX, panelY, index -> {
            int color = reliefColor(top.value(index), range[0], range[1], key, index, top);
            return deltaOverlay(color, delta.value(index));
        });
    }

    private static void drawScalarPanel(BufferedImage image,
                                        EndErosionTileKey key,
                                        int panelX,
                                        int panelY,
                                        FloatGrid values) {
        drawCore(image, key, panelX, panelY, index -> drainageColor(values.value(index)));
    }

    private static void drawCore(BufferedImage image,
                                 EndErosionTileKey key,
                                 int panelX,
                                 int panelY,
                                 IntGrid colors) {
        int halo = key.haloSamples();
        int coreWidth = key.sampleWidth() - halo * 2;
        int coreHeight = key.sampleHeight() - halo * 2;
        for (int z = 0; z < coreHeight; z++) {
            int pixelMinY = panelY + z * PANEL_SIZE / coreHeight;
            int pixelMaxY = panelY + (z + 1) * PANEL_SIZE / coreHeight;
            for (int x = 0; x < coreWidth; x++) {
                int pixelMinX = panelX + x * PANEL_SIZE / coreWidth;
                int pixelMaxX = panelX + (x + 1) * PANEL_SIZE / coreWidth;
                int index = (z + halo) * key.sampleWidth() + x + halo;
                int color = colors.value(index);
                for (int pixelY = pixelMinY; pixelY < pixelMaxY; pixelY++) {
                    for (int pixelX = pixelMinX; pixelX < pixelMaxX; pixelX++) {
                        image.setRGB(pixelX, pixelY, color);
                    }
                }
            }
        }
    }

    private static float[] coreRange(EndErosionTileKey key, FloatGrid values) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        int halo = key.haloSamples();
        for (int z = halo; z < key.sampleHeight() - halo; z++) {
            for (int x = halo; x < key.sampleWidth() - halo; x++) {
                float value = values.value(z * key.sampleWidth() + x);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return new float[] {min, max};
    }

    private static int reliefColor(float value,
                                   float min,
                                   float max,
                                   EndErosionTileKey key,
                                   int index,
                                   FloatGrid values) {
        float range = Math.max(1.0F, max - min);
        float alpha = Math.clamp((value - min) / range, 0.0F, 1.0F);
        int base = alpha < 0.58F
                ? interpolateColor(35, 58, 52, 93, 117, 78, alpha / 0.58F)
                : interpolateColor(93, 117, 78, 174, 170, 151,
                        (alpha - 0.58F) / 0.42F);

        int x = index % key.sampleWidth();
        int z = index / key.sampleWidth();
        int west = z * key.sampleWidth() + Math.max(0, x - 1);
        int east = z * key.sampleWidth() + Math.min(key.sampleWidth() - 1, x + 1);
        int north = Math.max(0, z - 1) * key.sampleWidth() + x;
        int south = Math.min(key.sampleHeight() - 1, z + 1) * key.sampleWidth() + x;
        float light = Math.clamp(0.78F
                + (values.value(west) - values.value(east)
                + values.value(north) - values.value(south)) * 0.018F,
                0.45F, 1.15F);
        return argb(channel(base, 16) * light,
                channel(base, 8) * light, channel(base, 0) * light);
    }

    private static int deltaOverlay(int baseColor, float deltaBlocks) {
        float magnitude = Math.clamp(Math.abs(deltaBlocks) / DELTA_SCALE_BLOCKS, 0.0F, 1.0F);
        if (magnitude <= 1.0E-5F) {
            return baseColor;
        }
        float alpha = 0.18F + 0.72F * (float) Math.sqrt(magnitude);
        int overlay = deltaBlocks < 0.0F ? argb(239, 91, 48) : argb(48, 164, 195);
        return blend(baseColor, overlay, alpha);
    }

    private static int drainageColor(float value) {
        float alpha = (float) Math.sqrt(Math.clamp(value, 0.0F, 1.0F));
        return alpha < 0.68F
                ? interpolateColor(18, 22, 25, 39, 160, 178, alpha / 0.68F)
                : interpolateColor(39, 160, 178, 244, 208, 84,
                        (alpha - 0.68F) / 0.32F);
    }

    private static int interpolateColor(int startRed,
                                        int startGreen,
                                        int startBlue,
                                        int endRed,
                                        int endGreen,
                                        int endBlue,
                                        float alpha) {
        float clamped = Math.clamp(alpha, 0.0F, 1.0F);
        return argb(
                Math.round(startRed + (endRed - startRed) * clamped),
                Math.round(startGreen + (endGreen - startGreen) * clamped),
                Math.round(startBlue + (endBlue - startBlue) * clamped));
    }

    private static int blend(int base, int overlay, float alpha) {
        float clamped = Math.clamp(alpha, 0.0F, 1.0F);
        int red = Math.round(channel(base, 16) * (1.0F - clamped)
                + channel(overlay, 16) * clamped);
        int green = Math.round(channel(base, 8) * (1.0F - clamped)
                + channel(overlay, 8) * clamped);
        int blue = Math.round(channel(base, 0) * (1.0F - clamped)
                + channel(overlay, 0) * clamped);
        return argb(red, green, blue);
    }

    private static int channel(int color, int shift) {
        return color >>> shift & 0xFF;
    }

    private static int argb(float red, float green, float blue) {
        return argb(Math.round(red), Math.round(green), Math.round(blue));
    }

    private static int argb(int red, int green, int blue) {
        return 0xFF000000
                | Math.clamp(red, 0, 255) << 16
                | Math.clamp(green, 0, 255) << 8
                | Math.clamp(blue, 0, 255);
    }

    private static int[] rgb(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static Path outputPath() {
        String configured = System.getProperty(OUTPUT_PROPERTY);
        return configured == null || configured.isBlank()
                ? Path.of("build", "reports", "erosion", OUTPUT_NAME)
                : Path.of(configured);
    }

    private static void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

    private record CandidateSet(EndErosionTile input,
                                float[] analyticalTopBlocks,
                                float[] analyticalDeltaBlocks,
                                float[] analyticalDrainagePotential,
                                float[] thermalTopBlocks,
                                float[] thermalDeltaBlocks,
                                EndHydraulicErosionTile hydraulic,
                                EndBoundedFlowErosionTile flow) {
    }

    private record AnalyticalResult(float[] topBlocks,
                                    float[] deltaBlocks,
                                    float[] drainagePotential) {
    }

    private record ThermalResult(float[] topBlocks, float[] deltaBlocks) {
    }

    @FunctionalInterface
    private interface FloatGrid {
        float value(int index);
    }

    @FunctionalInterface
    private interface IntGrid {
        int value(int index);
    }
}
