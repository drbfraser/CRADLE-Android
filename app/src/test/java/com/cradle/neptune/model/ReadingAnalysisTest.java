package com.cradle.neptune.model;

import com.cradle.neptune.R;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.cradle.neptune.model.ReadingAnalysis.RED_DIASTOLIC;
import static com.cradle.neptune.model.ReadingAnalysis.RED_SYSTOLIC;
import static com.cradle.neptune.model.ReadingAnalysis.YELLOW_DIASTOLIC;
import static com.cradle.neptune.model.ReadingAnalysis.YELLOW_SYSTOLIC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Testing ReadingAnalysis")
class ReadingAnalysisTest {

    private ReadingAnalysis readingAnalysis;

    @Mock
    private Reading readingMock;

    @BeforeEach
    void beforeEach(){
        //need  to init the mocks
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @DisplayName("Test null reading input")
    void analyzeNull() {
        readingAnalysis = ReadingAnalysis.analyze(null);
        assertEquals(readingAnalysis,ReadingAnalysis.NONE);
    }

    @Nested
    @DisplayName("Testing Differnt input into BP and heart rate")
    class Analyze {
        @BeforeEach
        void setup(){
            when(readingMock.getBpSystolic()).thenReturn(80);
            when(readingMock.getBpDiastolic()).thenReturn(80);
            when(readingMock.getHeartRateBPM()).thenReturn(80);
        }


        @Test
        @DisplayName("Testing null Systolic BP input")
        void analyzeWhenSystolicNull() {
            // important: mocks works on only functions, if returning
            when(readingMock.getBpSystolic()).thenReturn(null);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(readingAnalysis,ReadingAnalysis.NONE);
        }

        @Test
        @DisplayName("Testing Severe shock RED DOWN")
        void analyzeSevereShockRedDown() {
            when(readingMock.getHeartRateBPM()).thenReturn(160);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.RED_DOWN,readingAnalysis);
        }

        @Test
        @DisplayName("Testing RED UP via high BP systolic")
        void analyzeWhenBPHighViaSystolicRedUp() {
            when(readingMock.getBpSystolic()).thenReturn(RED_SYSTOLIC);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.RED_UP,readingAnalysis);
        }

        @Test
        @DisplayName("Testing RED UP via high BP Diastolic")
        void analyzeWhenBPHighViaDiastolicRedUp() {
            when(readingMock.getBpDiastolic()).thenReturn(RED_DIASTOLIC);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.RED_UP,readingAnalysis);
        }

        @Test
        @DisplayName("Testing  shock YELLOW DOWN")
        void analyzeShockYellowDown() {
            when(readingMock.getHeartRateBPM()).thenReturn(80);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.YELLOW_DOWN,readingAnalysis);
        }

        @Test
        @DisplayName("Testing Yellow UP via high BP systolic")
        void analyzeWhenBPHighViaSystolicYellowUp() {
            when(readingMock.getBpSystolic()).thenReturn(YELLOW_SYSTOLIC);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.YELLOW_UP,readingAnalysis);
        }

        @Test
        @DisplayName("Testing Yellow UP via high BP Diastolic")
        void analyzeWhenBPHighViaDiastolicYellowUp() {
            when(readingMock.getBpDiastolic()).thenReturn(YELLOW_DIASTOLIC);
            when(readingMock.getHeartRateBPM()).thenReturn(0);

            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.YELLOW_UP,readingAnalysis);
        }

        @Test
        @DisplayName("Testing normal GREEN")
        void  analyzeNormalGreen(){
            when(readingMock.getHeartRateBPM()).thenReturn(0);
            readingAnalysis = ReadingAnalysis.analyze(readingMock);
            assertEquals(ReadingAnalysis.GREEN,readingAnalysis);
        }

    }


}