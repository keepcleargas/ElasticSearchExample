/**
 * Copyright (C) 2010 Peter Karich <jetwick_@_pannous_._info>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetwick.ese.ui;

import de.jetwick.ese.search.MyQuery;
import de.jetwick.ese.util.FacetHelper;
import de.jetwick.ese.util.MapEntry;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.action.search.SearchResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxFallbackLink;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.elasticsearch.search.facet.filter.FilterFacet;
import org.elasticsearch.search.facet.terms.TermsFacet;

/**
 *
 * @author Peter Karich, peat_hal 'at' users 'dot' sourceforge 'dot' net
 */
public class FacetPanel extends Panel {

    private Map<String, String> tr = new LinkedHashMap<String, String>();
    private Set<String> alreadyFiltered = new LinkedHashSet<String>();
    private List<Entry<String, List<FacetHelper>>> normalFacetFields = new ArrayList<Entry<String, List<FacetHelper>>>();
    private ListView tagView;

    public FacetPanel(String id) {
        super(id);

        tr.put("userName", "User");

        tagView = new ListView("filterNames", normalFacetFields) {

            @Override
            public void populateItem(final ListItem item) {
                final Entry<String, List<FacetHelper>> entry = (Entry<String, List<FacetHelper>>) item.getModelObject();

                String keyValue = translate(entry.getKey());
                String filter = getFilterName(entry.getKey());
                if (filter != null) {
                    item.add(new LabeledLink("filterName", "< " + keyValue) {

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            onFacetChange(target, entry.getKey(), null, true);
                        }
                    }.add(new AttributeAppender("title", new Model("Remove all filters from '" + keyValue + "'"), " ")));
                } else
                    item.add(new Label("filterName", keyValue));

                item.add(new ListView("filterValues", entry.getValue()) {

                    @Override
                    protected void populateItem(ListItem li) {
                        final FacetHelper h = (FacetHelper) li.getModelObject();

                        final String filter = h.getFilter();
                        final boolean selected = alreadyFiltered.contains(filter);

                        Link link = new IndicatingAjaxFallbackLink("filterValueLink") {

                            @Override
                            public void onClick(AjaxRequestTarget target) {
                                onFacetChange(target, h.key, h.value, selected);
                            }
                        };
                        // change style if filter is selected
                        if (selected)
                            link.add(new AttributeAppender("class", new Model("filter-rm"), " "));
                        else
                            link.add(new AttributeAppender("class", new Model("filter-add"), " "));

                        link.add(new Label("filterValue", h.displayName));

                        // not clickable if filter would result in 0 docs
                        if (h.count == 0) {
                            link.setEnabled(false);
                            link.add(new AttributeAppender("class", new Model("gray"), " "));
                        }

                        li.add(new Label("filterCount", " (" + h.count + ")"));
                        li.add(link);
                    }
                });
            }
        };

        add(tagView);
    }

    public String getFilterName(String name) {
        name += ":";
        for (String filter : alreadyFiltered) {
            if (filter.startsWith(name)) {
                return filter.substring(name.length());
            }
        }
        return null;
    }

    public void update(SearchResponse rsp, MyQuery query) {
        normalFacetFields.clear();
        if (rsp != null) {
            for (Entry<String, List<FacetHelper>> entry : createFacetsFields(rsp)) {
                if (entry != null) {
                    normalFacetFields.add(entry);
                }
            }
        }
        alreadyFiltered = new LinkedHashSet<String>();
        for (String f : query.getFilters()) {
            alreadyFiltered.add(f);
        }
    }

    public void onFacetChange(AjaxRequestTarget target, String filter, Object val, boolean selected) {
    }

    public boolean isAlreadyFiltered(String filter) {
        return alreadyFiltered.contains(filter);
    }

    public String translate(String str) {
        String val = tr.get(str);
        if (val == null)
            return str;

        return val;
    }

    /**
     * Make sure that the facets appear in the order we defined via filterToIndex
     */
    public List<Entry<String, List<FacetHelper>>> createFacetsFields(SearchResponse rsp) {
        // be able to define the order of the facets here
        final int MAX_VAL = 5;
        Map<String, Integer> filterToIndex = new LinkedHashMap<String, Integer>() {

            {
                put("userName", 1);
            }
        };
        List<Entry<String, List<FacetHelper>>> ret = new ArrayList<Entry<String, List<FacetHelper>>>();
        for (int ii = 0; ii < MAX_VAL + 1; ii++) {
            ret.add(null);
        }

        if (rsp != null) {
            Facets facets = rsp.facets();
            if (facets != null)
                for (Facet facet : facets.facets()) {
                    if (facet instanceof TermsFacet) {
                        TermsFacet ff = (TermsFacet) facet;
                        Integer integ = filterToIndex.get(ff.getName());
                        if (integ != null && ff.entries() != null) {
                            List<FacetHelper> list = new ArrayList<FacetHelper>();
                            String key = ff.getName();
                            for (TermsFacet.Entry e : ff.entries()) {
                                String term = e.getTerm();
                                if ("T".equals(term))
                                    term = "true";
                                else if ("F".equals(term))
                                    term = "false";

                                list.add(new FacetHelper(key, term, term, e.getCount()));
                            }
                            ret.set(integ, new MapEntry(ff.getName(), list));
                        }
                    } else if (facet instanceof FilterFacet) {
                        FilterFacet ff = (FilterFacet) facet;
                        String name = ff.getName();
                        int firstIndex = name.indexOf(":");
                        if (firstIndex < 0)
                            continue;

                        String key = name.substring(0, firstIndex);
                        String val = name.substring(firstIndex + 1);
                        System.out.println("NAME:"+name);
                        // exclude smaller zero?
                        Long count = ff.count();
                        if (count == null)
                            count = 0L;

                        Integer index = filterToIndex.get(key);
                        if (index == null)
                            continue;

                        Entry<String, List<FacetHelper>> facetEntry = ret.get(index);
                        List<FacetHelper> list;
                        if (facetEntry == null) {
                            facetEntry = new MapEntry(key, new ArrayList<FacetHelper>());
                            ret.set(index, facetEntry);
                        }

                        list = facetEntry.getValue();
                        list.add(new FacetHelper(key, val, translate(name), count));
                    }

                }
        }
        return ret;
    }
}
