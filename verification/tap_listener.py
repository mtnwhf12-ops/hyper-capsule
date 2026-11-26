import re, sys
path, target = sys.argv[1], sys.argv[2]
xml = open(path, encoding='utf-8', errors='replace').read()
nodes = re.findall(r'<node [^>]*/?>', xml)
def center(bounds):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if not m: return None
    x1,y1,x2,y2 = map(int, m.groups())
    return f"{(x1+x2)//2} {(y1+y2)//2}"
def bounds_of(n):
    m = re.search(r'bounds="([^"]+)"', n)
    return m.group(1) if m else None
for i, n in enumerate(nodes):
    text = re.search(r'text="([^"]*)"', n)
    desc = re.search(r'content-desc="([^"]*)"', n)
    t = (text.group(1) if text else '') + ' ' + (desc.group(1) if desc else '')
    if target.lower() in t.lower():
        if target.lower() == 'allow':
            b = bounds_of(n);  c = center(b) if b else None
            if c: print(c); sys.exit(0)
        for m in nodes[i+1:i+6]:
            cls = re.search(r'class="([^"]*)"', m)
            if cls and ('Switch' in cls.group(1) or 'CheckBox' in cls.group(1) or 'Toggle' in cls.group(1)):
                b = bounds_of(m); c = center(b) if b else None
                if c: print(c); sys.exit(0)
        b = bounds_of(n); c = center(b) if b else None
        if c: print(c); sys.exit(0)
sys.exit(1)
