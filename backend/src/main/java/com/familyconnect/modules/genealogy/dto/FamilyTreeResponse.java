package com.familyconnect.modules.genealogy.dto;

import java.util.List;
import java.util.Map;

public class FamilyTreeResponse {
    private List<Node> nodes;
    private List<Edge> edges;

    public FamilyTreeResponse(List<Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<Node> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }

    public static class Node {
        private String id;
        private String type = "personNode";
        private Map<String, Object> data;

        public Node(String id, Map<String, Object> data) {
            this.id = id;
            this.data = data;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public Map<String, Object> getData() { return data; }
    }

    public static class Edge {
        private String id;
        private String source;
        private String target;
        private String type; // "blood" hoặc "marriage"

        public Edge(String id, String source, String target, String type) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.type = type;
        }

        public String getId() { return id; }
        public String getSource() { return source; }
        public String getTarget() { return target; }
        public String getType() { return type; }
    }
}

