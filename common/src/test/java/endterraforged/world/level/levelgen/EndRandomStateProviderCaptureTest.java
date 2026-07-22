package endterraforged.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EndRandomStateProviderCaptureTest {

    @AfterEach
    void clearCapture() {
        EndRandomStateProviderCapture.clear();
    }

    @Test
    void takeReturnsCapturedProviderOnlyOnce() {
        HolderGetter.Provider provider = emptyProvider();

        EndRandomStateProviderCapture.capture(provider);

        assertSame(provider, EndRandomStateProviderCapture.take());
        assertNull(EndRandomStateProviderCapture.take());
    }

    @Test
    void captureDoesNotCrossWorkerThreads() throws InterruptedException {
        HolderGetter.Provider provider = emptyProvider();
        AtomicReference<HolderGetter.Provider> workerValue = new AtomicReference<>();
        EndRandomStateProviderCapture.capture(provider);

        Thread worker = new Thread(() -> workerValue.set(EndRandomStateProviderCapture.take()));
        worker.start();
        worker.join();

        assertNull(workerValue.get());
        assertSame(provider, EndRandomStateProviderCapture.take());
    }

    private static HolderGetter.Provider emptyProvider() {
        return new HolderGetter.Provider() {
            @Override
            public <T> Optional<HolderGetter<T>> lookup(
                    ResourceKey<? extends Registry<? extends T>> registryKey) {
                return Optional.empty();
            }
        };
    }
}
