import os
import sys

# Lets `pytest` run from any directory, not only `python -m pytest` inside jobs/pipeline.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.dirname(__file__))
