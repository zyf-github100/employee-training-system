"""Create the portable certificate font from the upstream OFL TrueType variable font."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / ".local/pdf-font-tools"))
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

font = TTFont(root / ".local/NotoSansSC-VF.ttf")
font = instantiateVariableFont(font, {"wght": 400}, inplace=True)
for entry in font["name"].names:
    replacement = {1: "Zhixing Certificate Sans", 3: "ZhixingCertificateSans-Regular-1",
                   4: "Zhixing Certificate Sans Regular", 6: "ZhixingCertificateSans-Regular",
                   16: "Zhixing Certificate Sans", 17: "Regular"}.get(entry.nameID)
    if replacement:
        entry.string = replacement.encode(entry.getEncoding())
target = root / "src/main/resources/fonts/CertificateSans-Regular.ttf"
font.save(target)
print(f"Prepared {target.name}: {target.stat().st_size} bytes")
