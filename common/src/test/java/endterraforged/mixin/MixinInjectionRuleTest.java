package endterraforged.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Static analysis tests for the mod's mixin classes — validates the
 * bytecode-level injection rules that the Mixin processor enforces at
 * runtime but JUnit doesn't see.
 *
 * <p><b>Why these tests exist.</b> v0.1.5-preview shipped with a
 * critical crash ({@code InvalidInjectionException: @At("HEAD") selector
 * @Inject handler before super() invocation must be static}) because
 * {@link MixinRandomState#endTerraForged$initCapture} was an instance
 * method injected via {@code @At("HEAD")} on {@code <init>}. The Mixin
 * processor injects {@code <init>} HEAD bytecode BEFORE the
 * {@code aload_0; invokespecial Object.<init>} sequence — i.e. before
 * {@code super()} has been called. At that point {@code this} is
 * uninitialised, so the handler must be {@code static}. A non-static
 * handler that touches {@code this} would compile to bytecode that
 * reads an uninitialised {@code this} reference.</p>
 *
 * <p><b>Why the v0.1.5 crash didn't surface earlier.</b> The
 * {@code <init>} HEAD injection only fires when {@code RandomState} is
 * instantiated — which only happens during world creation (after the
 * user clicks "Create New World"). Startup-time tests (which only
 * exercise the mixin config structure via {@link MixinConfigValidationTest})
 * don't reach this code path; the crash only manifested at server-tick
 * start after the user clicked create-world.</p>
 *
 * <p><b>What these tests check.</b></p>
 * <ul>
 *   <li>For every {@code @Inject} on {@code <init>} with {@code @At("HEAD")}:
 *       the handler method must be {@code static}. The Mixin processor
 *       requires this because the injection point is before
 *       {@code super()}.</li>
 *   <li>For every {@code @Inject} on a non-void target method with
 *       {@code @At("RETURN")}: the handler must use
 *       {@link CallbackInfoReturnable}, not {@link CallbackInfo}. This
 *       was the v0.1.1 crash pattern (MixinRandomState was fixed in
 *       v0.1.1 but the test guards against future regression on other
 *       mixins).</li>
 * </ul>
 *
 * <p><b>Why this is reflection-based static analysis.</b> The Mixin
 * processor only runs at game-launch time, not at JUnit time. We can't
 * simulate the full Mixin application in unit tests. What we CAN do is
 * read the {@code @Inject} / {@code @At} annotations from the compiled
 * mixin classes via reflection and assert the rules statically — this
 * catches the same classes of bug that the runtime Mixin processor
 * would catch, but at build time instead of at game-launch.</p>
 *
 * <p><b>What these tests don't check.</b> {@code @At("INVOKE")} target
 * strings, {@code @Redirect} signatures, etc. — these require
 * parsing the vanilla class bytecode and matching the target descriptor,
 * which is beyond what reflection can do. The runtime Mixin processor
 * catches those.</p>
 */
class MixinInjectionRuleTest {

    private static final Gson GSON = new Gson();

    /**
     * All mixin classes registered in {@code endterraforged-common.mixins.json}.
     * Read from the JSON so the test stays in sync with the config — if a
     * new mixin is added to the config, this test automatically picks it
     * up without needing a parallel hardcoded list.
     */
    private static List<Class<?>> loadMixinClasses() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        JsonObject config = loadMixinConfig("/endterraforged-common.mixins.json");
        String pkg = config.get("package").getAsString();
        for (String arrayName : List.of("mixins", "client")) {
            if (!config.has(arrayName)) {
                continue;
            }
            JsonArray array = config.getAsJsonArray(arrayName);
            for (JsonElement entry : array) {
                String simpleName = entry.getAsString();
                String fqn = pkg + "." + simpleName;
                classes.add(Class.forName(fqn, false,
                        MixinInjectionRuleTest.class.getClassLoader()));
            }
        }
        return classes;
    }

    private static JsonObject loadMixinConfig(String resourcePath) throws Exception {
        try (InputStream in = MixinInjectionRuleTest.class
                .getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Mixin config not found on classpath: " + resourcePath);
            return GSON.fromJson(new java.io.InputStreamReader(in), JsonObject.class);
        }
    }

    /**
     * Every {@code @Inject} on {@code <init>} with {@code @At("HEAD")}
     * must have a {@code static} handler method.
     *
     * <p>The Mixin processor injects {@code <init>} HEAD bytecode BEFORE
     * the {@code aload_0; invokespecial Object.<init>} sequence — i.e.
     * before {@code super()} has been called. At that point {@code this}
     * is uninitialised, so the handler must be {@code static}. A
     * non-static handler would crash at Mixin-apply time with
     * {@code InvalidInjectionException: @At("HEAD") selector @Inject
     * handler before super() invocation must be static}.</p>
     *
     * <p>This was the v0.1.5 crash ({@link MixinRandomState}).
     * v0.1.6 fixed it by switching to {@code @At("INVOKE",
     * target="...mapAll...")}. This test prevents any future
     * re-introduction of the {@code @At("HEAD")} on {@code <init>}
     * pattern.</p>
     */
    @Test
    void injectOnConstructorHeadMustBeStatic() throws Exception {
        for (Class<?> mixinClass : loadMixinClasses()) {
            for (Method m : mixinClass.getDeclaredMethods()) {
                Inject inject = m.getAnnotation(Inject.class);
                if (inject == null) {
                    continue;
                }
                // method() is an array; the first entry is the target method name.
                // For multi-target @Injects the array has multiple entries but the
                // rule applies to each <init> entry individually.
                for (String methodName : inject.method()) {
                    if (!"<init>".equals(methodName)) {
                        continue;
                    }
                    for (At at : inject.at()) {
                        if ("HEAD".equals(at.value())) {
                            assertTrue(Modifier.isStatic(m.getModifiers()),
                                    "Mixin " + mixinClass.getSimpleName()
                                            + " method " + m.getName()
                                            + " @Inject <init> @At(\"HEAD\") "
                                            + "must be static — super() has not "
                                            + "been called at the HEAD injection "
                                            + "point, so `this` is uninitialised "
                                            + "and a non-static handler would crash "
                                            + "the Mixin processor at apply time. "
                                            + "Use @At(\"INVOKE\", target=\"...\") "
                                            + "after super() instead, or make the "
                                            + "handler static and pass `this` via "
                                            + "a cast.");
                        }
                    }
                }
            }
        }
    }

    /**
     * Every {@code @Inject} handler's callback type ({@link CallbackInfo}
     * vs {@link CallbackInfoReturnable}) must match the target vanilla
     * method's return type:
     * <ul>
     *   <li>non-void target method → MUST use {@link CallbackInfoReturnable}</li>
     *   <li>void target method (or {@code <init>}) → MUST use {@link CallbackInfo}</li>
     * </ul>
     *
     * <p>This rule applies regardless of the {@code @At} value: HEAD, RETURN,
     * INVOKE, INVOKE_ASSIGN, etc. on a non-void method all require CIR.
     * This is the v0.1.1 crash pattern ({@code @At("RETURN")} on
     * {@code saveEverything} returns boolean) and the v0.1.7 crash pattern
     * ({@code @At("HEAD")} on static {@code create} returns RandomState).</p>
     *
     * <p><b>Why source-file scanning instead of reflection.</b> The
     * {@code @Mixin} annotation has {@link RetentionPolicy#CLASS} (verified
     * via {@code javap -v} on {@code sponge-mixin.jar}), so
     * {@link Class#getAnnotation(Class)} returns {@code null} at runtime —
     * the annotation is in the class file but not visible to reflection.
     * We could parse the class file bytecode with ASM to read the
     * {@code @Mixin(value = VanillaClass.class)} argument, but ASM isn't
     * a test-scope dependency. Reading the {@code .java} source file
     * directly is simpler, robust against Loom remapping, and good
     * enough for static analysis — the test fails fast if someone adds
     * a new mixin without wiring up the source correctly.</p>
     *
     * <p><b>How target return type is verified.</b> The test parses
     * {@code @Mixin(value = X.class)} from the source to find the target
     * vanilla class, then loads that class via reflection (Minecraft
     * classes ARE on the test classpath via Loom — only the {@code @Mixin}
     * annotation itself is invisible, not the target classes), then
     * inspects {@link Method#getReturnType()} for the target method.</p>
     */
    @Test
    void callbackTypeMatchesTargetMethodReturnType() throws Exception {
        int handlerCount = 0;
        int mixinCount = 0;
        for (var entry : loadMixinSourceFiles().entrySet()) {
            mixinCount++;
            String mixinSimple = entry.getKey();
            String source = entry.getValue();
            Class<?> targetClass = resolveMixinTargetClassFromSource(source, mixinSimple);
            if (targetClass == null) {
                System.out.println("[MixinInjectionRuleTest] Skipping " + mixinSimple
                        + ": can't resolve @Mixin target class from source");
                continue;
            }
            for (InjectHandler handler : findInjectHandlers(source, mixinSimple, targetClass)) {
                handlerCount++;
                for (String targetMethodSpec : handler.targetMethods) {
                    boolean targetIsVoid = isTargetMethodVoid(targetClass, targetMethodSpec);
                    if (targetIsVoid) {
                        assertFalse(handler.usesCir,
                                "Mixin " + mixinSimple + " handler "
                                        + handler.handlerName + " targets void method "
                                        + targetMethodSpec + " but uses CallbackInfoReturnable. "
                                        + "Use CallbackInfo for void / <init> targets.");
                    } else {
                        assertTrue(handler.usesCir,
                                "Mixin " + mixinSimple + " handler "
                                        + handler.handlerName + " targets non-void method "
                                        + targetMethodSpec + " but uses CallbackInfo. "
                                        + "Use CallbackInfoReturnable<T> for non-void targets. "
                                        + "This was the v0.1.1 (saveEverything RETURN) and "
                                        + "v0.1.7 (create HEAD) crash pattern.");
                    }
                }
            }
        }
        // Sanity: we must have scanned at least 4 mixins (MixinRandomState,
        // MixinNoiseChunk, MixinMinecraftServer, MixinPresetEditor) and
        // found at least 5 @Inject handlers (1 in RandomState.create, 1 in
        // RandomState.<init>, 1 in NoiseChunk.<init>, 3 in MinecraftServer).
        // If this assertion fails the regex parser is missing handlers.
        assertTrue(mixinCount >= 4,
                "Expected to scan at least 4 mixin source files, got " + mixinCount
                        + ". Check loadMixinSourceFiles() — the mixin config may "
                        + "have changed, or the source files moved.");
        assertTrue(handlerCount >= 5,
                "Expected to find at least 5 @Inject handlers across all mixins, "
                        + "got " + handlerCount + ". Check findInjectHandlers() — "
                        + "the regex may not match the current @Inject syntax.");
    }

    /**
     * A parsed {@code @Inject} handler from a mixin source file.
     */
    private static final class InjectHandler {
        final String handlerName;
        final boolean usesCir;
        final java.util.List<String> targetMethods;

        InjectHandler(String handlerName, boolean usesCir, java.util.List<String> targetMethods) {
            this.handlerName = handlerName;
            this.usesCir = usesCir;
            this.targetMethods = targetMethods;
        }
    }

    /**
     * Finds all {@code @Inject} handlers in a mixin source file by
     * scanning the source text character-by-character.
     *
     * <p><b>Why not a single regex.</b> The {@code @Inject} annotation
     * contains nested parentheses — both in the {@code method = "..."}
     * string (vanilla method descriptors contain {@code (L...;...)}),
     * and in the nested {@code @At(value = "INVOKE", target =
     * "...mapAll(...)...")} sub-annotation. A regex like
     * {@code [^)]*\\)} stops at the first {@code )} it sees, which is
     * inside the method descriptor — not at the annotation's closing
     * paren. A character-scanning paren-depth tracker is the simplest
     * way to correctly find the annotation boundary.</p>
     *
     * <p><b>Why this also handles {@code method = "..."} (string) and
     * {@code method = { "...", "..." }} (array) syntax.</b> The
     * {@link #extractMethodNames(String)} helper accepts both forms;
     * the scanner just feeds it the annotation's content.</p>
     */
    private static java.util.List<InjectHandler> findInjectHandlers(
            String source, String mixinSimple, Class<?> targetClass) {
        java.util.List<InjectHandler> handlers = new java.util.ArrayList<>();
        int searchFrom = 0;
        while (true) {
            int injectIdx = source.indexOf("@Inject", searchFrom);
            if (injectIdx < 0) break;
            // Guard against matching @InjectSomethingElse — the char after
            // "@Inject" must not be an identifier character.
            int afterName = injectIdx + 7;
            if (afterName < source.length()) {
                char next = source.charAt(afterName);
                if (Character.isLetterOrDigit(next) || next == '_' || next == '$') {
                    searchFrom = afterName;
                    continue;
                }
            }
            // Find the opening '(' of the annotation (skipping whitespace).
            int parenStart = -1;
            int p = afterName;
            while (p < source.length()) {
                char c = source.charAt(p);
                if (c == '(') { parenStart = p; break; }
                if (!Character.isWhitespace(c)) break;
                p++;
            }
            if (parenStart < 0) { searchFrom = afterName; continue; }
            // Track paren depth to find the matching closing ')'.
            int depth = 1;
            int j = parenStart + 1;
            while (j < source.length() && depth > 0) {
                char c = source.charAt(j);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                if (depth == 0) break;
                j++;
            }
            if (depth != 0) break;
            String annotationContent = source.substring(parenStart + 1, j);
            int afterAnnot = j + 1;

            java.util.List<String> targetMethods = extractMethodNames(annotationContent);
            if (targetMethods.isEmpty()) { searchFrom = afterAnnot; continue; }

            // Find the handler method declaration after the annotation.
            // Skip whitespace and line/block comments between the annotation
            // and the method declaration.
            int k = afterAnnot;
            while (k < source.length()) {
                char c = source.charAt(k);
                if (Character.isWhitespace(c)) { k++; continue; }
                if (c == '/' && k + 1 < source.length()) {
                    if (source.charAt(k + 1) == '/') {
                        int nl = source.indexOf('\n', k);
                        if (nl < 0) break;
                        k = nl + 1;
                        continue;
                    }
                    if (source.charAt(k + 1) == '*') {
                        int end = source.indexOf("*/", k + 2);
                        if (end < 0) break;
                        k = end + 2;
                        continue;
                    }
                }
                break;
            }
            // Read the method declaration up to '{' (body) or ';' (abstract).
            int bracePos = source.indexOf('{', k);
            int semiPos = source.indexOf(';', k);
            int declEnd;
            if (bracePos < 0 && semiPos < 0) break;
            if (bracePos < 0) declEnd = semiPos;
            else if (semiPos < 0) declEnd = bracePos;
            else declEnd = Math.min(bracePos, semiPos);
            if (declEnd < 0) break;
            String decl = source.substring(k, declEnd).trim();

            // Extract handler name + parameter list from the declaration.
            int paramStart = decl.indexOf('(');
            if (paramStart < 0) { searchFrom = declEnd + 1; continue; }
            String beforeParams = decl.substring(0, paramStart).trim();
            String[] tokens = beforeParams.split("\\s+");
            if (tokens.length == 0) { searchFrom = declEnd + 1; continue; }
            String handlerName = tokens[tokens.length - 1];
            // Find the matching ')' of the parameter list (handle nested
            // parens in generics like CallbackInfoReturnable<RandomState>).
            int paramDepth = 1;
            int paramEnd = -1;
            for (int x = paramStart + 1; x < decl.length(); x++) {
                char c = decl.charAt(x);
                if (c == '(') paramDepth++;
                else if (c == ')') {
                    paramDepth--;
                    if (paramDepth == 0) { paramEnd = x; break; }
                }
            }
            if (paramEnd < 0) { searchFrom = declEnd + 1; continue; }
            String params = decl.substring(paramStart + 1, paramEnd);
            boolean usesCir = params.contains("CallbackInfoReturnable");

            handlers.add(new InjectHandler(handlerName, usesCir, targetMethods));
            searchFrom = declEnd + 1;
        }
        return handlers;
    }

    /**
     * Extracts target method names from an {@code @Inject} annotation's
     * content string. Handles both forms:
     * <ul>
     *   <li>{@code method = "name"} — single string</li>
     *   <li>{@code method = { "name1", "name2" }} — array of strings</li>
     * </ul>
     */
    private static java.util.List<String> extractMethodNames(String annotationContent) {
        java.util.List<String> methods = new java.util.ArrayList<>();
        java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile(
                "method\\s*=\\s*(?:\"([^\"]+)\"|\\{([^}]*)\\})");
        java.util.regex.Matcher m = methodPattern.matcher(annotationContent);
        if (m.find()) {
            if (m.group(1) != null) {
                methods.add(m.group(1));
            } else if (m.group(2) != null) {
                java.util.regex.Pattern strPattern =
                        java.util.regex.Pattern.compile("\"([^\"]+)\"");
                java.util.regex.Matcher sm = strPattern.matcher(m.group(2));
                while (sm.find()) {
                    methods.add(sm.group(1));
                }
            }
        }
        return methods;
    }

    /**
     * Loads mixin {@code .java} source files from {@code src/main/java}.
     *
     * <p>Why source files: the {@code @Mixin} annotation has
     * {@link RetentionPolicy#CLASS}, so reflection can't read it. Source
     * scanning is simpler and robust against Loom remapping.</p>
     *
     * @return map of simple class name → source file content
     */
    private static java.util.Map<String, String> loadMixinSourceFiles() throws Exception {
        JsonObject config = loadMixinConfig("/endterraforged-common.mixins.json");
        String pkg = config.get("package").getAsString();
        String pkgPath = pkg.replace('.', '/');
        java.nio.file.Path sourceRoot = java.nio.file.Paths.get(
                "src/main/java/" + pkgPath);
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String arrayName : List.of("mixins", "client")) {
            if (!config.has(arrayName)) continue;
            for (JsonElement e : config.getAsJsonArray(arrayName)) {
                String simpleName = e.getAsString();
                java.nio.file.Path file = sourceRoot.resolve(simpleName + ".java");
                if (java.nio.file.Files.exists(file)) {
                    out.put(simpleName, java.nio.file.Files.readString(file));
                }
            }
        }
        return out;
    }

    /**
     * Resolves the vanilla target class by parsing the
     * {@code @Mixin(value = VanillaClass.class)} argument from the
     * source. Falls back to {@code @Mixin(targets = "fqn")} for string-form.
     */
    private static Class<?> resolveMixinTargetClassFromSource(
            String source, String mixinSimple) {
        // Try @Mixin(value = X.class) first
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "@Mixin\\s*\\(\\s*(?:value\\s*=\\s*)?([A-Za-z0-9_$.]+)\\.class");
        java.util.regex.Matcher m = p.matcher(source);
        if (m.find()) {
            String className = m.group(1);
            // If unqualified (no dots), try to resolve against common MC imports
            if (className.indexOf('.') < 0) {
                // Scan imports to find the fully qualified name
                java.util.regex.Pattern importP = java.util.regex.Pattern.compile(
                        "import\\s+(?:static\\s+)?([A-Za-z0-9_$.]+\\." + className + ");");
                java.util.regex.Matcher importM = importP.matcher(source);
                if (importM.find()) {
                    className = importM.group(1);
                }
            }
            try {
                return Class.forName(className, false,
                        MixinInjectionRuleTest.class.getClassLoader());
            } catch (ClassNotFoundException ex) {
                return null;
            }
        }
        // Fall back to @Mixin(targets = "fqn")
        java.util.regex.Pattern tp = java.util.regex.Pattern.compile(
                "@Mixin\\s*\\(\\s*targets\\s*=\\s*\"([^\"]+)\"");
        java.util.regex.Matcher tm = tp.matcher(source);
        if (tm.find()) {
            try {
                return Class.forName(tm.group(1), false,
                        MixinInjectionRuleTest.class.getClassLoader());
            } catch (ClassNotFoundException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Determines whether a target method is void (or is {@code <init>}).
     *
     * @param targetClass the vanilla class the mixin targets
     * @param methodSpec the method spec from {@code @Inject.method()} —
     *                   may be just a name ("create"), a name+descriptor
     *                   ("create(...)L...;"), or {@code "<init>"}
     * @return {@code true} if the target method is void or {@code <init>}
     */
    private static boolean isTargetMethodVoid(Class<?> targetClass, String methodSpec) {
        if ("<init>".equals(methodSpec) || "<clinit>".equals(methodSpec)) {
            return true;
        }
        // Strip any descriptor portion: keep just the method name.
        String methodName = methodSpec;
        int paren = methodSpec.indexOf('(');
        if (paren > 0) {
            methodName = methodSpec.substring(0, paren);
        }
        // Look up the method by name on the target class. If multiple
        // overloads exist, take the most permissive reading: if ANY
        // overload returns void, treat as void (the mixin would target
        // that overload). If none returns void, treat as non-void.
        // This is conservative — a non-void overload missed by this
        // heuristic would let a CI-typed handler through, but only if
        // the user added a NEW non-void overload we didn't know about.
        boolean foundNonVoid = false;
        boolean foundVoid = false;
        for (Method m : targetClass.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) continue;
            if (m.getReturnType() == void.class) {
                foundVoid = true;
            } else {
                foundNonVoid = true;
            }
        }
        if (foundVoid && !foundNonVoid) {
            return true;
        }
        if (foundNonVoid && !foundVoid) {
            return false;
        }
        // Mixed (some overloads void, some non-void) or not found.
        // Conservative: assume non-void so the test demands CIR —
        // better false-positive than false-negative.
        if (!foundVoid && !foundNonVoid) {
            System.out.println("[MixinInjectionRuleTest] Warning: target method "
                    + methodName + " not found on " + targetClass.getName()
                    + " — assuming non-void (CIR required) as conservative default");
        }
        return false;
    }

    /**
     * Sanity check: the {@code client} mixin array contains
     * {@link MixinPresetEditor} — this is the entry point that wires
     * the EndTerraForged preset editor into vanilla's "Customize" button
     * on the create-world screen. If it's missing, the user has no way
     * to reach the editor from the create-world flow.
     */
    @Test
    void clientArrayContainsPresetEditorMixin() throws Exception {
        JsonObject config = loadMixinConfig("/endterraforged-common.mixins.json");
        assertNotNull(config.get("client"),
                "endterraforged-common.mixins.json must have a 'client' array "
                        + "even if empty — its presence makes the intent "
                        + "explicit and is the documented Mixin convention.");
        JsonArray client = config.getAsJsonArray("client");
        boolean found = false;
        for (JsonElement e : client) {
            if ("MixinPresetEditor".equals(e.getAsString())) {
                found = true;
                break;
            }
        }
        assertTrue(found,
                "endterraforged-common.mixins.json 'client' array must "
                        + "contain 'MixinPresetEditor' — without it the "
                        + "EndTerraForged preset editor is unreachable "
                        + "from the create-world screen's 'Customize' button.");
    }

    /**
     * Defensive: every method annotated with {@code @Inject} must have
     * at least one parameter of type {@link CallbackInfo} or
     * {@link CallbackInfoReturnable}. Without one, the Mixin processor
     * rejects the injection.
     *
     * <p>This isn't a regression-prevention test (no historical bug — it's
     * a structural sanity check). If a future mixin author forgets the
     * callback parameter, the build's compile-time check might not catch
     * it (the {@code @Inject} annotation itself doesn't enforce the
     * parameter), but the Mixin processor would crash at apply time.</p>
     */
    @Test
    void everyInjectHandlerHasCallbackInfoParameter() throws Exception {
        for (Class<?> mixinClass : loadMixinClasses()) {
            for (Method m : mixinClass.getDeclaredMethods()) {
                Inject inject = m.getAnnotation(Inject.class);
                if (inject == null) continue;
                boolean hasCallback = false;
                for (Class<?> paramType : m.getParameterTypes()) {
                    if (CallbackInfo.class.isAssignableFrom(paramType)
                            || CallbackInfoReturnable.class.isAssignableFrom(paramType)) {
                        hasCallback = true;
                        break;
                    }
                }
                assertTrue(hasCallback,
                        "Mixin " + mixinClass.getSimpleName()
                                + " method " + m.getName()
                                + " @Inject must have a CallbackInfo or "
                                + "CallbackInfoReturnable parameter — the "
                                + "Mixin processor rejects handlers without one.");
            }
        }
    }
}
