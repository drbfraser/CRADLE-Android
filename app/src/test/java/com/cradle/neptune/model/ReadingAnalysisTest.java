package com.cradle.neptune.model;

import com.cradle.neptune.R;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReadingAnalysisTest {
    ReadingAnalysis readingAnalysis;

    @Test
    void analyzeNull() {
        readingAnalysis = ReadingAnalysis.analyze(null);
        assertEquals(readingAnalysis,ReadingAnalysis.NONE);
    }

    @Test
    void analyzeNone() {
        readingAnalysis = ReadingAnalysis.analyze(null);
        assertEquals(readingAnalysis,ReadingAnalysis.NONE);
    }

    @Test
    void getAnalysisText() {
    }

    @Test
    void getBriefAdviceText() {
    }



}