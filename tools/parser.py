import os
import argparse
from config import get_java_boilerplate_excludes
from search import search_with_ripgrep


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Search for a term in a repository, excluding boilerplate files and directories."
    )
    parser.add_argument("search_term", help="The term to search for.")
    parser.add_argument("repo_path", help="The path to the repository to search.")
    args = parser.parse_args()

    java_excludes = get_java_boilerplate_excludes()

    if not os.path.isdir(args.repo_path):
        print(f"Error: The specified repository path does not exist or is not a directory: {args.repo_path}")
    else:
        search_with_ripgrep(args.search_term, args.repo_path, java_excludes)