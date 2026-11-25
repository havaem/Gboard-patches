package dev.jason.gboardpatches.patches.gboard.features.customstickers

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardCustomStickersOpeningTabPatch = bytecodePatch(
    description = "讓使用者設定按下表情符號鍵時預設開啟的分頁（表情符號、貼圖、GIF 或顏文字）。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(
            classType = ART_EXTENSION_CLASS,
            name = ART_EXTENSION_ACTIVATE_METHOD,
            returnType = "Z",
            parameterTypes = ART_EXTENSION_ACTIVATE_PARAMETERS,
        ).applyOpeningTabOverride()
    }
}

/**
 * ArtExtension.onActivate picks the expression keyboard to open (stock: always emoji) and then
 * opens it at the "opening expression keyboard" log. Right after it reads the internal-editor
 * flag, ask the runtime for the user's tab; a non-null answer jumps straight to that log with
 * the chosen keyboard type, otherwise stock selection runs unchanged.
 */
internal fun MutableMethod.applyOpeningTabOverride() {
    val abi = RuntimeAbiCatalog.abi(RuntimeCallId.CUSTOM_STICKERS_RUNTIME_RESOLVE_OPENING_KEYBOARD)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1) { "Malformed opening tab override in $definingClass->$name" }
        return
    }
    check(implementation!!.registerCount == STOCK_REGISTER_COUNT) {
        "Unexpected register count in $definingClass->$name: ${implementation!!.registerCount}"
    }

    val internalEditorReads = instructions.indices.filter {
        instructions[it].isMethodReference(INTERNAL_EDITOR_GETTER)
    }
    check(internalEditorReads.size == 1) {
        "Expected one $INTERNAL_EDITOR_GETTER call in $definingClass->$name"
    }
    val readIndex = internalEditorReads.single()
    val read = instructions[readIndex] as FiveRegisterInstruction
    val result = instructions.getOrNull(readIndex + 1)
    check(
        read.registerC == EXTENSION_HOST_REGISTER &&
            result?.isOpcode("MOVE_RESULT") == true &&
            (result as OneRegisterInstruction).registerA == INTERNAL_EDITOR_REGISTER,
    ) {
        "$INTERNAL_EDITOR_GETTER result is not in the expected registers"
    }
    val managerInit = instructions.indices.filter {
        instructions[it].isMethodReference(CORPUS_MANAGER_INIT)
    }
    check(
        managerInit.size == 1 && managerInit.single() < readIndex &&
            (instructions[managerInit.single()] as FiveRegisterInstruction).registerC ==
            CORPUS_MANAGER_REGISTER,
    ) {
        "Expression corpus manager is not in v$CORPUS_MANAGER_REGISTER before the tab choice"
    }

    // The keyboard type is opened right after this log-owner read; v3 holds it there.
    val openTargets = instructions.withIndex().filter { (_, instruction) ->
        instruction.isOpcode("SGET_OBJECT") && instruction.isFieldReference(OPENING_LOGGER_FIELD)
    }
    check(openTargets.size == 1 && openTargets.single().index > readIndex) {
        "Expected one opening-keyboard log after the tab choice in $definingClass->$name"
    }

    addInstructionsWithLabels(
        readIndex + 2,
        """
            ${RuntimeCallEmitter.invoke(
                RuntimeCallId.CUSTOM_STICKERS_RUNTIME_RESOLVE_OPENING_KEYBOARD,
                "v$CORPUS_MANAGER_REGISTER, v$EDITOR_INFO_REGISTER, " +
                    "v$INTERNAL_EDITOR_REGISTER, v$ACTIVATION_FLAG_REGISTER",
            )}

            move-result-object v$SCRATCH_REGISTER

            if-eqz v$SCRATCH_REGISTER, :custom_stickers_stock_opening_tab

            check-cast v$SCRATCH_REGISTER, $KEYBOARD_TYPE

            move-object v$INTERNAL_EDITOR_REGISTER, v$SCRATCH_REGISTER

            goto :custom_stickers_opening_tab_chosen

            :custom_stickers_stock_opening_tab
            nop
        """.trimIndent(),
        ExternalLabel("custom_stickers_opening_tab_chosen", openTargets.single().value),
    )
}

// shortcut: obfuscated 18.5.4 names and registers (izb ArtExtension.onActivate, gyo
// ExpressionCorpusManager, qch keyboard type); move to version bindings when porting.
private const val ART_EXTENSION_CLASS = "Lizb;"
private const val ART_EXTENSION_ACTIVATE_METHOD = "ft"
private val ART_EXTENSION_ACTIVATE_PARAMETERS = listOf(
    "Lpev;", "Landroid/view/inputmethod/EditorInfo;", "Z", "Ljava/util/Map;", "Lohr;",
)
private const val INTERNAL_EDITOR_GETTER = "Loid;->ar()Z"
private const val CORPUS_MANAGER_INIT = "Lgyo;-><init>(Landroid/content/Context;Lqec;Lqvt;)V"
private const val KEYBOARD_TYPE = "Lqch;"
private const val OPENING_LOGGER_FIELD = "Lizb;->a:Lwvx;"
private const val STOCK_REGISTER_COUNT = 9
private const val SCRATCH_REGISTER = 0
private const val INTERNAL_EDITOR_REGISTER = 3 // then the chosen keyboard type at the log
private const val EXTENSION_HOST_REGISTER = 4
private const val EDITOR_INFO_REGISTER = 5
private const val ACTIVATION_FLAG_REGISTER = 6
private const val CORPUS_MANAGER_REGISTER = 7
