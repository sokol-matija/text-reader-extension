package net.falconparore.textreader

/**
 * Regex-based markdown stripper tuned for Kokoro TTS.
 *
 * Reads cleaner than a full parser would — strips the syntax characters
 * but preserves surrounding punctuation and newlines so the TTS engine
 * still paces headings and list items naturally.  Handles:
 *  - images ![alt](url) -> alt
 *  - links [text](url) -> text
 *  - fenced code blocks ``` ``` -> inner text
 *  - inline code `x` -> x
 *  - bold **x** / __x__, italic *x* / _x_, strikethrough ~~x~~
 *  - ATX headings (# through ######)
 *  - block quotes (> ...)
 *  - unordered and ordered list markers
 *  - horizontal rules (---, ***, ___)
 *  - collapses runs of blank lines
 */
object MarkdownStripper {
    fun strip(input: String): String {
        if (input.isBlank()) return input
        var s = input

        // Images first (so the following links rule doesn't eat them)
        s = s.replace(Regex("""!\[([^\]]*)]\(([^)]+)\)"""), "$1")
        // Links
        s = s.replace(Regex("""\[([^\]]+)]\(([^)]+)\)"""), "$1")

        // Fenced code blocks ```lang\n ... ```
        s = s.replace(Regex("""```[a-zA-Z0-9_+\-]*\n?([\s\S]*?)```"""), "$1")
        // Inline code `x`
        s = s.replace(Regex("""`([^`\n]+)`"""), "$1")

        // Bold + italic emphasis (order matters: bold before italic)
        s = s.replace(Regex("""\*\*\*([^*]+)\*\*\*"""), "$1")  // ***bold italic***
        s = s.replace(Regex("""___([^_]+)___"""), "$1")
        s = s.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")      // **bold**
        s = s.replace(Regex("""__([^_]+)__"""), "$1")
        s = s.replace(Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)"""), "$1")  // *italic*
        s = s.replace(Regex("""(?<!_)_([^_\n]+)_(?!_)"""), "$1")

        // Strikethrough
        s = s.replace(Regex("""~~([^~]+)~~"""), "$1")

        // ATX headings
        s = s.replace(Regex("""(?m)^\s{0,3}#{1,6}\s+"""), "")
        // Block quotes
        s = s.replace(Regex("""(?m)^\s{0,3}>\s?"""), "")
        // Unordered list markers
        s = s.replace(Regex("""(?m)^\s*[-*+]\s+"""), "")
        // Ordered list markers
        s = s.replace(Regex("""(?m)^\s*\d+\.\s+"""), "")

        // Horizontal rules on their own line
        s = s.replace(Regex("""(?m)^\s*([-*_])\1{2,}\s*$"""), "")

        // Collapse 3+ blank lines
        s = s.replace(Regex("""\n{3,}"""), "\n\n")

        return s.trim()
    }
}
