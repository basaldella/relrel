/*
 * 	Licensed under the Apache License, Version 2.0 (the "License");
 * 	you may not use this file except in compliance with the License.
 * 	You may obtain a copy of the License at
 *
 * 	     http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software
 * 	distributed under the License is distributed on an "AS IS" BASIS,
 * 	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 	See the License for the specific language governing permissions and
 * 	limitations under the License.
 */
package org.sssw.relrel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Get the most "uncommon" entities linked by a certain Wikipedia pages.
 *
 * @author Marco Basaldella
 */
public class FactFinder {

    /**
     * The user agent that will be used for HTTP requests (since Wikipedia
     * requests it).
     */
    private String userAgent;

    /**
     * The page search query that will be performed using the Wikipedia
     * OpenSearch APIs. Protocol, languages and the page queries need to be
     * appended before and after this string.
     */
    private final String singlePageQuery = "wikipedia.org/w/api.php?action=query&prop=categories|extracts|links&clshow=!hidden&format=json&pllimit=500&plnamespace=0&titles=";

    private final String categoryQueryBegin = "wikipedia.org/w/api.php?action=query&list=categorymembers&cmlimit=max&format=json&rawcontinue=";
    private final String categoryQueryEnd = "&cmtitle=Category:";

    // Blacklist of unwanted terms
    private static final List<String> blackTerms = Arrays.asList(new String[]{"null", "International Standard Book Number",
        "Digital object identifier",
        "Living people",
        "PubMed Identifier",
        "International Standard Serial Number",
        "Wikisource",
        "disambiguation",
        "stub",
        "Featured Articles"
    });

    /**
     * Maps the categories associated with a page.
     */
    private Map<String, Integer> categories
            = new HashMap<>();

    /**
     * Maps the related links (the "See Also" section) of a Wikipedia page.
     */
    private Map<String, Integer> links
            = new HashMap<>();

    /**
     * The page we're analyzing.
     */
    private String InputPage;

    /**
     * Set the user agent used for requests to Wikipedia.
     *
     * @param userAgent the user agent string
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * Run the UncommonFacts algorithm as defined in the project document.
     *
     * @param grams the grams to analyze.
     */
    public void findUncommonFacts(String inputPage) {

        this.InputPage = inputPage;

        scrapInputPage();

        System.out.println("*** Wikipedia page: " + InputPage);

        System.out.println();
        System.out.println("Found " + links.size() + " outgoing links");
        System.out.println("Found " + categories.size() + " categories");

        for (String cat : categories.keySet()) {
            System.out.print(cat + ";");
        }

        System.out.println();

        int counter = 1;

        for (String cat : categories.keySet()) {
            System.out.println("Analyzing category " + counter++ + "...");
            findFactsInCategory(cat);
        }

        List<Map.Entry<String, Integer>> ordered
                = links.entrySet().stream().sorted(Map.Entry.comparingByValue())
                //.limit(20)
                .collect(Collectors.toList());

        System.out.println("*** Suggestions ***");

        for (Map.Entry<String, Integer> entry : ordered) {
            System.out.println("" + entry.getKey() + " \t\t\t Score: " + entry.getValue());
        }

    } // void findUncommonFacts

    /**
     * Lines 1-3 of the algorithm: init the table with the outgoing links (and
     * find the categories).
     */
    private void scrapInputPage() {

        HttpURLConnection con = null;
        BufferedReader reader = null;

        InputPage = InputPage.replaceAll(" ", "_");

        // do the query and save the retrieved json in an object.
        String queryAddress = String.format("https://%s.%s%s",
                Locale.ENGLISH, singlePageQuery, InputPage);

        try {

            con = (HttpURLConnection) (new URL(queryAddress)).openConnection();
            con.setRequestProperty("User-Agent", userAgent);
            con.setRequestMethod("GET");
            reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            Object json = (new JSONParser()).parse(reader);
            // closing connection
            con.disconnect();
            // The retrieved JSON is something like:
            //
            // "query": {
            //        "pages": {
            //            "<PAGE ID NUMBER>": {
            //                "pageid": "<PAGE ID NUMBER>",
            //                "ns": 0,
            //                "title": "<PAGE TITLE>",
            //                "categories": [
            //                    {
            //                        "ns": 14,
            //                        "title": "Category:<CATEGORY 1>"
            //                    },
            //                    {
            //                        "ns": 14,
            //                        "title": "Category:<CATEGORY 2>"
            //                    },
            //                    {
            //                        "ns": 14,
            //                        "title": "Category:<CATEGORY 3>"
            //                    }
            //                ],
            //                "extract":"<TEXT>",
            //                "links": [
            //                    {
            //                        "ns": 0,
            //                        "title": "<LINK 1>"
            //                    },
            //                     {
            //                        "ns": 0,
            //                        "title": "<LINK 2>"
            //                    },
            //                    {
            //                        "ns": 0,
            //                        "title": "<LINK 3>"
            //                    }
            //                 ]
            //            }
            //        }
            //    }
            //}
            // note that NOT ALL the wikis have the "extract" property in the API
            // therefore we may not assume that it will always be there
            JSONObject queryblock = (JSONObject) json;
            JSONObject pagesBlock = (JSONObject) queryblock.get("query");
            JSONObject idBlock = (JSONObject) pagesBlock.get("pages");

            // if we pipe'd more than one title, we'll have more than one pageId entry
            for (Iterator it = idBlock.keySet().iterator(); it.hasNext();) {

                String pageId = (String) it.next();
                JSONObject block = (JSONObject) idBlock.get(pageId);

                // iterate through categories
                JSONArray jsonCats = (JSONArray) block.get("categories");
                if (jsonCats != null) {
                    Iterator<JSONObject> iterator = jsonCats.iterator();
                    while (iterator.hasNext()) {
                        JSONObject category = (iterator.next());
                        String catName = (String) category.get("title");
                        catName = catName.replaceFirst("Category:", "");
                        catName = catName.replaceFirst("Categoria:", "");
                        if (!catName.toLowerCase().contains("stub")
                                && !catName.contains("Featured Articles")
                                && !catName.toLowerCase().contains("disambiguation")) {

                            if (!this.categories.containsKey(catName) && !blackTerms.contains(catName)) {
                                if (!catName.contains("births") && (!catName.contains("deaths"))) {
                                    this.categories.put(catName, 0);
                                }
                            }
                        }
                    }
                }

                // We can find related entities in the text
                // many articles have a "See Also" section that begins with
                //          <h2>See also</h2>\n<ul>
                // and ends with:
                //          </ul>
                // To retrieve these links, we don't need to scrap HTML.
                // We can just read the list of links included in the JSON
                // the drawback of this approach is that some pages have huge
                // amounts of links and many of them are uninteresting
                // For example, almost any page has a reference to the
                // definition of ISBN (contained in the references)
                // or of some other kind of wide-used identifier such as:
                // Pub-Med index,
                // Digital-Object-Identifier,
                // International Standard Book Number,
                // Wikisource, and so on.
                JSONArray jsonLinks = (JSONArray) block.get("links");
                if (jsonLinks != null) {
                    Iterator<JSONObject> iterator = jsonLinks.iterator();
                    while (iterator.hasNext()) {
                        JSONObject link = (iterator.next());
                        String linkname = (String) link.get("title");

                        if (!this.links.containsKey(linkname)
                                && !blackTerms.contains(linkname)) {
                            this.links.put(linkname, 0);
                        }

                    }
                }
            }

        } catch (ParseException ex) {
            throw new RuntimeException(
                    "Error while parsing JSON by Wikipedia for page: " + InputPage, ex);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(
                    "Malformed Wikipedia URL: " + queryAddress, ex);
        } catch (IOException ex) {
            throw new RuntimeException(
                    "Error while reading Wikipedia", ex);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                throw new RuntimeException(
                        "Error while closing Wikipedia stream", ex);
            }
        }

    }

    private void findFactsInCategory(String cat) {

        HttpURLConnection con = null;
        BufferedReader reader = null;

        System.out.println("Analyzing category : " + cat);

        cat = cat.replaceAll(" ", "_");

        String continueQuery = "";

        do {

            // do the query and save the retrieved json in an object.
            String queryAddress = String.format("https://%s.%s%s%s%s",
                    Locale.ENGLISH, categoryQueryBegin, continueQuery, categoryQueryEnd, cat);

            try {

                con = (HttpURLConnection) (new URL(queryAddress)).openConnection();
                con.setRequestProperty("User-Agent", userAgent);
                con.setRequestMethod("GET");
                reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                Object json = (new JSONParser()).parse(reader);
                // closing connection
                con.disconnect();

                JSONObject queryblock = (JSONObject) json;
                JSONObject mainBlock = (JSONObject) queryblock.get("query");
                JSONArray categoriesBlock = (JSONArray) mainBlock.get("categorymembers");

                Iterator<JSONObject> iterator = categoriesBlock.iterator();

                if (continueQuery.isEmpty()) {
                    System.out.println("This category has " + categoriesBlock.size() + " pages");
                } else {
                    System.out.println("Continuing previous category with " + categoriesBlock.size() + " pages");
                }

                int counter = 0;

                while (iterator.hasNext()) {
                    

                    if (counter%20 == 0)
                        System.out.println("Pages analyzed: " + (counter)
                                + " of " + categoriesBlock.size());
                    
                    counter++;
                    
                    JSONObject singleCategoryBlock = (iterator.next());
                    String pageName = (String) singleCategoryBlock.get("title");
                    pageName = pageName.replace(" ", "_");
                    
                    // Please be aware that the categories JSON returns not only
                    // pages, but also (sub) categories and other things we don't want.
                    // So, keep only the pages and skip the rest.
                    // For further information, please check
                    // https://en.wikipedia.org/wiki/Wikipedia:Namespace
                    long pageNamespace = (Long) singleCategoryBlock.get("ns");

                    if (!pageName.equals(InputPage) && pageNamespace == 0) {
                        findFactsInPage(pageName);
                    }

                }

                // Check if we need to continue
                // But before, reset the continuation id to ensure
                // termination of the do-while loop
                continueQuery = "";

                JSONObject continueBlock = (JSONObject) queryblock.get("query-continue");

                if (continueBlock != null) {
                    JSONObject cmBlock = (JSONObject) continueBlock.get("categorymembers");
                    continueQuery = (String) cmBlock.get("cmcontinue");
                    continueQuery = "&cmcontinue=" + continueQuery;
                }

            } catch (ParseException ex) {
                throw new RuntimeException(
                        "Error while parsing JSON by Wikipedia for page: " + cat, ex);
            } catch (MalformedURLException ex) {
                throw new RuntimeException(
                        "Malformed Wikipedia URL: " + queryAddress, ex);
            } catch (IOException ex) {
                throw new RuntimeException(
                        "Error while reading Wikipedia", ex);
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(
                            "Error while closing Wikipedia stream", ex);
                }
            }
        } while (!continueQuery.isEmpty());

    }

    private void findFactsInPage(String pageName) {

        HttpURLConnection con = null;
        BufferedReader reader = null;

        pageName = pageName.replaceAll(" ", "_");

        // do the query and save the retrieved json in an object.
        String queryAddress = String.format("https://%s.%s%s",
                Locale.ENGLISH, singlePageQuery, pageName);

        try {

            con = (HttpURLConnection) (new URL(queryAddress)).openConnection();
            con.setRequestProperty("User-Agent", userAgent);
            con.setRequestMethod("GET");
            reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            Object json = (new JSONParser()).parse(reader);
            // closing connection
            con.disconnect();

            JSONObject queryblock = (JSONObject) json;
            JSONObject pagesBlock = (JSONObject) queryblock.get("query");
            JSONObject idBlock = (JSONObject) pagesBlock.get("pages");

            for (Iterator it = idBlock.keySet().iterator(); it.hasNext();) {

                String pageId = (String) it.next();
                JSONObject block = (JSONObject) idBlock.get(pageId);

                JSONArray jsonLinks = (JSONArray) block.get("links");
                if (jsonLinks != null) {
                    Iterator<JSONObject> iterator = jsonLinks.iterator();
                    while (iterator.hasNext()) {
                        JSONObject link = (iterator.next());
                        String linkName = (String) link.get("title");

                        if (this.links.containsKey(linkName)) {
                            int newValue = links.get(linkName) + 1;
                            links.replace(linkName, newValue);
                        }

                    }
                }
            }

        } catch (ParseException ex) {
            throw new RuntimeException(
                    "Error while parsing JSON by Wikipedia for page: " + pageName, ex);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(
                    "Malformed Wikipedia URL: " + queryAddress, ex);
        } catch (IOException ex) {
            throw new RuntimeException(
                    "Error while reading Wikipedia", ex);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                throw new RuntimeException(
                        "Error while closing Wikipedia stream", ex);
            }
        }

    }

} // class

