package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.dto.FamilyTreeResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@SpringBootTest
@Transactional
public class GenealogyTreeServiceTest {

    @Autowired
    private GenealogyTreeService genealogyTreeService;

    @Test
    public void testGetTreePerformanceWithMockData() {
        UUID mockFamilyId = UUID.randomUUID();
        
        // Bấm giờ đo thời gian xử lý đệ quy cây dữ liệu
        long startTime = System.currentTimeMillis();
        
        FamilyTreeResponse response = genealogyTreeService.getTree(mockFamilyId, null, 5, "MEMBER");
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("====== THỜI GIAN PHẢN HỒI API CÂY GIA PHẢ: " + duration + " ms ======");
        
        // Kiểm tra tiêu chí hoàn thành của Jira: Phải chạy dưới 500ms
        Assertions.assertTrue(duration < 500, "Lỗi hiệu năng: Thời gian phản hồi cây gia phả vượt quá 500ms!");
        Assertions.assertNotNull(response);
    }
}

