== Build ==

Build:

  plugin-Library$ ant


== Javadoc ==

If you want to generate Javadocs, download bliki-doclet, which is a little
something I cooked up to have mediawiki markup instead of ugly HTML in javadoc
comments, and put it into the doc/ directory:

  $EXT$ wget "http://cloud.github.com/downloads/infinity0/bliki-doclet/bliki-doclet_openjdk-6-src-b16-24_apr_2009.jar"
  plugin-Library/doc$ ln -s $EXT/bliki-doclet*.jar bliki-doclet.jar
  plugin-Library$ ant javadoc


== Using Library ==

Enter a search query into the search box, this can use the standard search engine
query syntax ( or, not/-, "") or any combination

eg
	freenet -"freenet message system"
	freenet or "free network"

Stop words( Anything less than 3 letters and popular words such as 'and' 'the'
'but' ) are excluded from searches, in some situations such as an intersection
search ('bill of rights') or phrase search('"jesus of nazareth"') the stopword
is treated as a blank as the result can still be useful, searching for
the phrase ('"the who"') will fail though as ignoring 'the' makes the search almost meaningless.

You also need to specify one or more indexes, there are checkboxes for the 2
largest xml indexes known about and others can be specified in the uri box
separated by spaces, just enter the uri or local path.

You can add bookmarks to other indexes by specifying a name, they aren't saved
currently but will soon.

You can select 'Group sites and editions' this will put all pages under the same
key into groups. The sorting on relevance wont work so well but it depends what
you are looking for as to which is more useful.

Only the newest editions of sites will be shown by default, select 'Show older
editions' and all editions wil be shown but the older ones will be greyed out.


== Inline searches (provisional) ==

You can provide a search box in your freesite, by using the html below, though
this may change soon :

<form action="/plugins/plugin.Library.FreesiteSearch" method="POST">
<input type="text" name="search" />
<input type="hidden" name="index" value="[[[index uri here]]]" />
<input type="submit" />
</form>


== Ongoing work to split ==

The plugin is in src, test (for historical reasons).

The uploader (standalone program) is in uploader/src and uploader/test depending on fcp and plugin.
