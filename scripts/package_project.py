"""Archive les sources versionnées, le dossier et le JAR local, sans les données."""
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
files = subprocess.check_output(["git", "-C", str(ROOT), "ls-files", "-z"]).decode("utf-8").split("\0")
archive = ROOT / "output/M-Motors_Bloc3_Adam.zip"
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as output:
    for name in files:
        if name and not name.endswith(".zip"):
            output.write(ROOT / name, "m-motors/" + name)
    jar = ROOT / "backend/target/m-motors-1.0.0.jar"
    if not jar.exists():
        raise RuntimeError("Construire le JAR avec mvn verify avant de préparer l'archive.")
    output.write(jar, "m-motors/backend/target/" + jar.name)
    for relative in ("backend/target/site/jacoco", "backend/target/surefire-reports"):
        for path in (ROOT / relative).rglob("*"):
            if path.is_file():
                output.write(path, "m-motors/" + path.relative_to(ROOT).as_posix())
    history = subprocess.check_output(["git", "-C", str(ROOT), "log", "--graph", "--oneline", "--all"]).decode("utf-8")
    output.writestr("m-motors/output/historique-git.txt", history)
with zipfile.ZipFile(archive) as result:
    if result.testzip() is not None:
        raise RuntimeError("L'archive est corrompue.")
    forbidden = [name for name in result.namelist() if any(part in name.split("/") for part in ("node_modules", "data", ".tools", ".git"))]
    if forbidden:
        raise RuntimeError(f"Fichiers non prévus dans l'archive : {forbidden}")
print(f"Archive vérifiée : {archive} ({archive.stat().st_size:,} octets)")
