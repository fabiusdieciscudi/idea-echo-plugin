package name.fabius10scudi.ideaecho

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/** Highlights nearby word repetitions in .tex files, only while the tool window is visible. */
class EchoAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        val vFile = element.virtualFile ?: return
        if (!vFile.name.endsWith(".tex", ignoreCase = true)) return
        val state = EchoHighlightState.getInstance(element.project)
        if (!state.isEnabled()) return
        val editorBackground = state.editorBackground()

        val settings = EchoSettings.getInstance(element.project)
        val params = settings.toParams()
        for (echo in RepetitionAnalyzer.analyze(element.text, params)) {
            val alpha = WordColors.alphaFor(echo.minGap, params.windowSize)
            val bg = WordColors.colorFor(
                echo.word, echo.sameSentence, editorBackground, echo.minGap, params.windowSize
            )
//            thisLogger().debug {
//                "echo '${echo.word}' gap=${echo.minGap} window=${params.windowSize} " +
//                    "alpha=$alpha bg=$bg over=$editorBackground"
//            }
            val attrs = TextAttributes().apply {
                backgroundColor = bg
                effectType = EffectType.BOXED
                effectColor = bg.darker()
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(echo.startOffset, echo.endOffset))
                .enforcedTextAttributes(attrs)
                .tooltip(
                    EchoBundle.message(
                        "annotator.echo.tooltip", echo.word, echo.minGap, "%.2f".format(alpha)
                    )
                )
                .create()
        }
    }
}