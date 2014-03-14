package com.atlassian.plugins.tutorial.rest;

import javax.xml.bind.annotation.*;

import com.atlassian.plugins.rest.common.security.AnonymousAllowed;

import java.util.List;
import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.status.*;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A resource of message.
 */
@Path("/message")
public class MyRestResource {

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService             searchService;
    private final CustomFieldManager        customFieldManager;

    public MyRestResource(final SearchService searchService, final JiraAuthenticationContext authenticationContext, CustomFieldManager customFieldManager)
    {
        this.searchService         = searchService;
        this.authenticationContext = authenticationContext;
        this.customFieldManager    = customFieldManager;
    }

    @GET
    @AnonymousAllowed
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response getMessage(@QueryParam("key") String key)
    {

        // The search interface requires JQL clause... so let's build one
        JqlClauseBuilder jqlClauseBuilder = JqlQueryBuilder.newClauseBuilder();
        // Our JQL clause is simple project="TUTORIAL"
        jqlClauseBuilder.addCondition("sprint in openSprints()");
        // A page filter is used to provide pagination. Let's use an unlimited filter to
        // to bypass pagination.
        PagerFilter pagerFilter = PagerFilter.getUnlimitedFilter();
        com.atlassian.jira.issue.search.SearchResults searchResults = null;
        try {
            // Perform search results
            searchResults = searchService.search(authenticationContext.getLoggedInUser(), jqlClauseBuilder.buildQuery(), pagerFilter);
        } catch (SearchException e) {
            e.printStackTrace();
        }

        HashMap<String, Sprint> sprints = new HashMap<String, Sprint>();
        List<Issue> issues = searchResults.getIssues();

        String keyValue = null;
        StringTokenizer tok = null;
        Map<String, String> map = null;

        long tempNewEstimate = 0, estimate = 0, spent = 0;

        for (Issue issue : issues){
            CustomField customField = customFieldManager.getCustomFieldObject("customfield_10007");
            Object customFieldValue = customField.getValue(issue);

            if(customFieldValue != null) {

                keyValue = customFieldValue.toString().substring(customFieldValue.toString().indexOf("[") + 1, customFieldValue.toString().lastIndexOf("]"));
                tok = new StringTokenizer(keyValue, ",");
                map = new LinkedHashMap<String, String>();
                while (tok.hasMoreTokens()) {
                    String entString = tok.nextToken();
                    map.put(entString.split("=")[0], entString.split("=")[1]);
                }

                if(map.get("state") != null && map.get("state").equals("ACTIVE")) {

                    Sprint sprint = null;
                    if(sprints.containsKey(map.get("name"))) {
                        sprint = sprints.get(map.get("name"));
                    } else {
                        sprint = new Sprint();
                        sprint.name = map.get("name");
                    }
                    sprint.estimate += (estimate = issue.getOriginalEstimate() != null ? issue.getOriginalEstimate() : 0);
                    sprint.spent    += (spent    = issue.getTimeSpent() != null ? issue.getTimeSpent() : 0);

                    Status st = issue.getStatusObject();
                    if(st != null && st.getName().equals("Resolved") == false) {

                        tempNewEstimate = estimate - spent;
                        if(tempNewEstimate < 0)
                            tempNewEstimate = 0;

                        sprint.newEstimate += tempNewEstimate;
                    }

                    try {

                        String[] s = map.get("startDate").split("T");
                        s = s[0].split("-");
                        sprint.startDate = s[2] + "/" + s[1] + "/" + s[0];

                        s = map.get("endDate").split("T");
                        s = s[0].split("-");
                        sprint.endDate = s[2] + "/" + s[1] + "/" + s[0];
                    } catch(Exception e) {
                        e.printStackTrace();
                    }



                    sprints.put(map.get("name"), sprint);
                }
            }
        }

        return Response.ok(new SprintList(sprints)).build();
    }

    @GET
    @AnonymousAllowed
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Path("/{key}")
    public Response getMessageFromPath(@PathParam("key") String key)
    {
        return Response.ok(new MyRestResourceModel(key, getMessageFromKey(key))).build();
    }

    private String getMessageFromKey(String key) {
        // In reality, this data would come from a database or some component
        // within the hosting application, for demonstration purpopses I will
        // just return the key
        return key;
    }
}

@XmlRootElement
class SprintList {
    @XmlElement
    Map<String, Sprint> sprints;

    public SprintList(Map<String, Sprint> sprints) {
        this.sprints = sprints;
    }
}

class Sprint {

    @XmlElement
    public String name = "";

    @XmlElement
    public double estimate = 0;

    @XmlElement
    public double spent;

    @XmlElement
    public double newEstimate;

    @XmlElement
    public String startDate;

    @XmlElement
    public String endDate;

}