# Wiki Page Types

In the wiki, there's two main types of pages - static wiki pages and dynamic wiki pages, each with their own use cases.

{table_of_contents}

## Static wiki pages

Static wiki pages, despite the name, may still have content that is dynamically generated. The key feature however, is
that these pages are defined individually in the code and often contain their own independent templates.

This page for example, is a static page as the number of pages does not change depending on the content. These differ
from dynamic pages which are explained in [dynamically-generated wiki pages](#dynamically-generated-wiki-pages).

Generally, you should choose static pages for any game content for which the number of pages would not change depending
on the addition of new content (Eg. `Ashes` could be a static page, but the page for each specific ash type should be
dynamically generated).

### How static pages are organised in the codebase

Static pages like all pages typically feature a generator functions and get added to the page directory and hierarchy in
the dedicated `add_static_pages()` function in `pages.py`. Most static pages should be both in the page hierarchy and
the directory, however some, like the sidebar, are only in the page directory.

### Updating & adding static pages

If you wish to update an existing static page, this is usually pretty simple. Generally, I'd recommend exploring the
`add_static_pages()` function to find the relevant page and then navigate to the generator function to see how it is
generated, along with the associated template. Here, you can make edits as necessary

If you wish to add a new static page, you'll need to define your own generator function in `pages.py`,
create a template file (if necessary), and then define the page in the directory and/or hierarchy in `add_static_pages()`

As a guide, you can follow the below checklist when adding new static pages:
1. Define a suitable Markdown template
2. Define a generator function
3. Add the page to the directory/hierarchy in `add_static_pages()`
4. Test it to make sure it shows up correctly

## Dynamically-generated wiki pages

Unlike static pages, dynamically-generated wiki pages are typically defined and added to the page directory at wiki runtime.
They typically use one template to generate a number of pages with related, but different content.

For example, the individual boss pages (eg. King Black Dragon, Demon Lord) are a good example of dynamically-generated pages as while there is a common template,
the number of pages differs depending on how many bosses are in the game at any one time.

### How dynamic pages are organised in the codebase

Dynamically-generated pages have a few differences compared to static pages being primarily that all generated pages
relating to a certain topic will usually share both a template and a parameterised generator function.

Unlike static pages, there is no specific defined function where dynamically-generated pages are defined in the directory
and hierarchy as they will use their own function that adds them to the hierarchy.

Using the boss pages as an example, you'll notice that the template `boss.md` uses a common, highly parameterised template
that all boss pages use. The function `gen_boss` generates content for each individual boss page. Notice how it has a `boss`
parameter which uses the dictionary passed in to generate the page. Finally, the `add_boss_pages()` function which defines
all the pages and adds them to the directory (and potentially hierarchy) - this function is called at the bottom of `pages.py`
in order to actually add them to the directory/hierarchy.

### Adding new dynamically-generated pages

If you're developing dynamically-generated pages, generally I'd recommend to first create an appropriate template file
(Eg. `boss.md`). Then you can define a generator function which generates the content itself (Eg. `gen_boss`) and finally create the appropriate function to define these pages and add them to the
directory/hierarchy (Eg. `add_boss_pages()`). Make sure to add a call to this final function at the bottom of `pages.py`.

When adding these pages to the hierarchy, using a collapsible hierarchy ensures it doesn't result in a messy navigation bar.

Additionally, if the page is a dedicated page for an item, etc, you should keep the same name as used in the json files
for the page ID so that they can be easily referenced by other pages.

As a guide, you can use the following checklist when adding new dynamically-generated pages:
1. Define a suitable shared Markdown template
2. Define a parameterised generator function which has any relevant data passed in (loading can still be done in the function but it could be more performant to delegate to the caller)
3. Create a function that defines these pages and adds them to the directory/hierarchy
4. Add a statement to call the function at the bottom of `pages.py`
5. Test and ensure all the pages come up properly

#### Tip: Footer tables

If you have a specific section of the game which has a lot of dynamically generated links (eg. Combat which encompasses
bosses, enemies, & dungeons), it's often worthwhile to create a set of footer links which you can see an example of with
`gen_combat_footer()` - Note this generator function does not have a dedicated page as it is a component of other pages.
