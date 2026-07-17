# Getting started - Wiki contributions

Great! You want to know how you can contribute to the wiki and make it better over time. While this wiki functions a little differently from most you may have worked on in the past, if you have a little bit of programming knowledge you'll find it easy to work with, and even if you don't have programming knowledge there are still some ways you can help out.

If you want to make changes to the game itself, check out {game_contribution_link}.

{{table_of_contents}}

## How the wiki works

Unlike most wikis which have a collection of pages that require constant maintenance from the community whenever a game is updated, Idle Fantasy relies on a dynamically generated wiki which uses actual game data to generate the individual pages.

It uses Markdown to define templates with Python being responsible for the compilation and generation of dynamic content.

Some content still requires manual maintenance such as strategy guides and some text content however a number of changes in the game is reflected in the wiki.

## Core terminology

To best understand how you can work with the wiki codebase (and also understand the jargon in the docs), it's helpful to go over some core terminology:

### Page Directory

The page directory is responsible for holding information about all the pages used within the wiki. It gets populated with information about all pages before the actual generation of any content and allows you to refer to other pages when defining page content through the use of a page ID (e.g. for links).

Pages are added to the page directory in `pages.py` under the `Page Listings` section.

### Page Hierarchy

The page hierarchy is responsible primarily for controlling the navigation used within the sidebar but also on the home page. It lets you have precise control over how the navigation shows including the ordering of items. When adding pages to the page hierarchy, you can do so by "merging" it with a list using the `PageHierarchy.merge()` method. You can see examples of this in the generated pages.

Pages are added to the page hierarchy in `pages.py` under the `Page Listings` section.

### Generator functions

Generator functions are responsible for the actual content generation itself. They work by reading game data and filling out a template file based upon the actual content. There are two main ways that generator functions are defined depending on whether you are defining static pages or dynamically-generated pages.

These generator functions are defined in `pages.py` in the page generation section.

### Templates

Templates provide the base formatting for pages within the wiki. Whenever they refer to game content, they'll usually have a field defined like `{{{{field}}}}` that is then filled in using the `string.format()` method in Python. These are filled in by the generator functions described previously.

### Static/Dynamically-generated pages

Whenever we refer to static vs dynamically-generated pages this refers not to the content within pages but how the pages are defined. If pages are "hardcoded" in the `add_static_pages` function, these are static, whereas generated pages will produce an unknown amount of pages like with each individual boss page.

For more information, see {page_types_link}.

## Building/Compiling the Wiki

To build the wiki, you'll need to set up an appropriate Python environment which you can use to run the wiki compilation. For information about setting up virtual environments, see [this tutorial](https://www.geeksforgeeks.org/python/create-virtual-environment-using-venv-python/) (make sure to call it .venv to have it be gitignored).

From there, you can run the following command to see what you can do (from the project folder):

```bash
# Activate the virtual environment
.venv/Scripts/activate
# Change directory to wiki directory
cd wiki
# See help information for program
python -m src -h
```

## Wiki code structure

The following code files are used as follows in the wiki:

- `__main__.py` - The main entry point to the wiki program responsible for parsing arguments and top-level management.
- `page_hierarchy.py` - A simple file defining the page hierarchy structure.
- `pages.py` - The primary code responsible for generating all the Markdown pages. This also contains a number of helper functions which you should use where relevant such as `link()`, etc.
- `site.py` - The code responsible for generating the Idle Fantasy wiki website based upon the generated Markdown files.

## Contributing process

For small contributions (e.g. fixing small mistakes, etc), you can simply fork the repository, make the relevant change and then create a pull request.

If you notice larger issues/inaccuracies that might take some time to fix, you should create an issue beforehand and mention that you're planning on making the changes. This makes sure that there aren't multiple people working on the same issue (without knowing) and also means that the issue can be referenced/tracked.

If you want to make a number of new pages or significant changes (especially dynamically-generated ones) in order to improve the wiki, then you should open a discussion to give the opportunity for community involvement.

Additionally, you might notice a number of #Todo items in the wiki code. These are also great places to look for things that need doing that we maybe just haven't got around to sorting out yet.

## Additional topics

- {page_types_link} - More information about the different page types
