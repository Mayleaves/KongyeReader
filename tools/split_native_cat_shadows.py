"""Keep native cat shadow/body layers reproducible in generated Kotlin artwork."""
import re

def split_native_cat_shadows(source: str) -> str:
    helpers = []
    pattern = re.compile(
        r'^(internal fun DrawScope\.(draw(?:Imported|Minimal|GoldMin|Gold)(?:Focus|Happy|Sleep|Loaf))\([^\n]*\) \{\n)'
        r'((?:    (?:if \(shadow\) )?drawPath[^\n]*\n){1,2})', re.M)

    def replace(match):
        signature, name, lines = match.groups()
        if 'shadow: Boolean' not in signature:
            signature = signature.replace(') {', ', shadow: Boolean = true) {')
        plain = lines.replace('if (shadow) ', '')
        if f'fun DrawScope.{name}Shadow(' not in source:
            helpers.append(f'internal fun DrawScope.{name}Shadow(dark: Boolean) {{\n{plain}}}\n')
        guarded = plain.replace('    drawPath', '    if (shadow) drawPath')
        return signature + guarded

    source = pattern.sub(replace, source)
    return source.rstrip() + '\n\n' + '\n'.join(helpers) if helpers else source
