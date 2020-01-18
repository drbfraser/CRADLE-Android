package com.cradle.neptune.model;

import com.cradle.neptune.R;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ReadingAnalysisTest {

    private ReadingAnalysis readingAnalysis;

    @Mock
    private Reading readingMock;

    @BeforeEach
    void beforeAll(){
        //need  to init the mocks
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void analyzeNull() {
        readingAnalysis = ReadingAnalysis.analyze(null);
        assertEquals(readingAnalysis,ReadingAnalysis.NONE);
    }

    @Nested
    class Analyze {
        @BeforeEach
        void setup(){
            when(readingMock.getBpSystolic()).thenReturn(1);
            when(readingMock.getBpDiastolic()).thenReturn(1);
            when(readingMock.getHeartRateBPM()).thenReturn(1);
        }


        @Test
        void analyzeWhenSystolicNull() {
            // important: mocks works on only functions, if returning
            when(readingMock.getBpSystolic()).thenReturn(null);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(readingAnalysis,ReadingAnalysis.NONE);
        }
        @Test
        void analyzeWhenSystolicHighl() {
            when(readingMock.getBpSystolic()).thenReturn(2);
            when(readingMock.getHeartRateBPM()).thenReturn(4);

            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.RED_DOWN,readingAnalysis);
        }

    }

    @Test
    void getAnalysisText() {
    }

    @Test
    void getBriefAdviceText() {
    }



}