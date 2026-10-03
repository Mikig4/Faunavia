"""Run the identical Blender script using the official bpy headless module."""
import os
import pathlib
import runpy
import sys

sys.path.insert(0, os.environ["FAUNAVIA_BPY_MODULES"])
runpy.run_path(str(pathlib.Path(__file__).parent / "blackbird" / "create.py"), run_name="__main__")
