package com.familyconnect.modules.genealogy.dto;

import java.util.List;

public class RelationshipResponse {
    private String relationName; // Tên quan hệ (ví dụ: "Anh ruột", "Cô ruột")
    private String callAToB;     // Cách A xưng gọi với B
    private String callBToA;     // Cách B xưng gọi với A
    private List<String> path;   // Đường đi dạng danh sách tên các node trung gian

    public RelationshipResponse() {}

    public RelationshipResponse(String relationName, String callAToB, String callBToA, List<String> path) {
        this.relationName = relationName;
        this.callAToB = callAToB;
        this.callBToA = callBToA;
        this.path = path;
    }

    public String getRelationName() { return relationName; }
    public void setRelationName(String relationName) { this.relationName = relationName; }
    public String getCallAToB() { return callAToB; }
    public void setCallAToB(String callAToB) { this.callAToB = callAToB; }
    public String getCallBToA() { return callBToA; }
    public void setCallBToA(String callBToA) { this.callBToA = callBToA; }
    public List<String> getPath() { return path; }
    public void setPath(List<String> path) { this.path = path; }
}

