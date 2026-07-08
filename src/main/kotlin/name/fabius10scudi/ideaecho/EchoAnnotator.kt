package name.fabius10scudi.ideaecho

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
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
        if (!EchoHighlightState.getInstance(element.project).isEnabled()) return

        for (echo in RepetitionAnalyzer.analyze(element.text)) {
            val bg = WordColors.colorFor(echo.word, echo.sameSentence)
            val attrs = TextAttributes().apply {
                backgroundColor = bg
                effectType = EffectType.BOXED
                effectColor = bg.darker()
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(echo.startOffset, echo.endOffset))
                .enforcedTextAttributes(attrs)
                .tooltip("Echo: ${echo.word}")
                .create()
        }
    }
}