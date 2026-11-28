"""Kotlin string-literal scanner: finds "..." literals (not raw strings, not in comments), with template parts."""
import re

def scan(src):
    out = []; i = 0; n = len(src)
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            j = src.find('\n', i); i = n if j < 0 else j; continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3); i = n if j < 0 else j + 3
            while i < n and src[i] == '"': i += 1
            continue
        if c == "'":
            # char literal
            j = i + 1
            if j < n and src[j] == '\\': j += 2
            else: j += 1
            if j < n and src[j] == "'": i = j + 1; continue
            i += 1; continue
        if c == '"':
            lit, end = read_string(src, i)
            out.append(lit); i = end; continue
        i += 1
    return out

def read_string(src, start):
    """src[start] == '"'. Returns ({start,end,raw,parts}, end_index)."""
    i = start + 1; n = len(src); parts = []; buf = []
    while i < n:
        c = src[i]
        if c == '\\':
            buf.append(src[i:i+2]); i += 2; continue
        if c == '"':
            if buf: parts.append(('text', ''.join(buf)))
            return {'start': start, 'end': i + 1, 'raw': src[start:i+1], 'parts': parts}, i + 1
        if c == '$' and i + 1 < n and src[i+1] == '{':
            if buf: parts.append(('text', ''.join(buf))); buf = []
            j = i + 2; depth = 1
            while j < n and depth:
                if src[j] == '"':
                    if src.startswith('"""', j):
                        k = src.find('"""', j + 3); j = k + 3; continue
                    _, j = read_string(src, j); continue
                if src[j] == "'":
                    j += 3 if src[j+1] != '\\' else 4; continue
                if src[j] == '{': depth += 1
                elif src[j] == '}': depth -= 1
                j += 1
            parts.append(('expr', src[i+2:j-1])); i = j; continue
        if c == '$' and i + 1 < n and (src[i+1].isalpha() or src[i+1] == '_'):
            if buf: parts.append(('text', ''.join(buf))); buf = []
            m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', src[i+1:])
            parts.append(('expr', m.group(0))); i = i + 1 + len(m.group(0)); continue
        buf.append(c); i += 1
    raise ValueError('unterminated string at %d' % start)

def text_of(lit):
    return ''.join(p[1] if p[0] == 'text' else '{%s}' % p[1] for p in lit['parts'])

NONUI = re.compile(r'^(https?://|content://|file://|android\.|com\.|org\.|application/|image/|audio/|video/|text/|[a-z0-9_.:/\-]+$|[A-Z0-9_]+$|%|#|\.)')
def is_ui(lit):
    t = ''.join(p[1] for p in lit['parts'] if p[0] == 'text').strip()
    if len(t) < 2 or not re.search(r'[A-Za-z]{2}', t): return False
    # A template with words around its values ("$n files", "$a to $b") is UI even when it's all lowercase.
    if any(p[0] == 'expr' for p in lit['parts']) and re.search(r'(^|\s)[a-z]{2,}(\s|$)', t) and not re.search(r'[=/\\\[\]{}<>]|%\(|--', t): return True
    if NONUI.match(t): return False
    if re.match(r'^[a-z][A-Za-z0-9]*$', t): return False   # camelCase keys
    if re.search(r'[a-z]_[a-z]', t) and ' ' not in t: return False
    if t.endswith(('.mp3', '.png', '.jpg', '.pdf', '.json', '.enc', '.apk')) and ' ' not in t: return False
    return True
