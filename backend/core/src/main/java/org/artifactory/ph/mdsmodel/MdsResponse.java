package org.artifactory.ph.mdsmodel;

import lombok.Getter;

import java.util.List;

/**
 * This class is supposed to parse json response from MDS server
 * Response example:
 * {
 * "data":{
 *      "packages":{
 *          "pageInfo":{
 *              "hasNextPage":false,
 *              "endCursor":"UGFja2FnZTox"
 *          },
 *          "edges":[
 *              {
 *                  "node":{
 *                      "name":"next.whs.tools.dbmigrate-1",
 *                      "packageType":"nuget"
 *                  }
 *              }
 *          ]
 *      }
 *  }
 * }
 *
 * @author Volodymyr Shulga
 */

@Getter
public class MdsResponse {
    private Data data;

    @Getter
    public static class Data {
        private Packages packages;
    }

    @Getter
    public static class Packages {
        private PageInfo pageInfo;
        private List<Edge> edges;
    }

    @Getter
    public static class PageInfo {
        private boolean hasNextPage;
        private String endCursor;
    }

    @Getter
    public static class Edge {
        private Node node;
    }

    @Getter
    public static class Node {
        private String name;
        private String packageType;
    }
}