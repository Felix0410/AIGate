package com.felix.aigate.invocation;

import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.entity.InvocationStatus;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.invocation.service.InvocationTrackingFacade;
import com.felix.aigate.provider.adapter.InvalidProviderResponseException;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import com.felix.aigate.runtime.exception.ProxyErrorType;
import com.felix.aigate.runtime.service.ModelInvocationException;
import com.felix.aigate.runtime.service.ModelProxyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvocationTrackingFacadeTest {

    @Mock
    private ModelProxyService modelProxyService;

    @Mock
    private InvocationRecordMapper invocationRecordMapper;

    @InjectMocks
    private InvocationTrackingFacade facade;

    // ---- SUCCESS 路径回归 ----

    @Test
    @DisplayName("保存失败不能覆盖模型成功：Mapper 抛异常仍返回原 InvokeResponse")
    void mapperFailureShouldNotOverrideModelSuccess() {

        InvokeResponse expectedResponse = new InvokeResponse(
                "success content", "test-model", "stop");

        when(modelProxyService.invoke(anyLong(), any()))
                .thenReturn(expectedResponse);

        doThrow(new RuntimeException("db error"))
                .when(invocationRecordMapper).insert(any(InvocationRecord.class));

        InvokeRequest request = new InvokeRequest();
        InvokeResponse actual = facade.invoke(1L, request);

        assertSame(expectedResponse, actual);
        assertEquals("success content", actual.getContent());
        assertEquals("test-model", actual.getModel());
        assertEquals("stop", actual.getFinishReason());

        verify(modelProxyService).invoke(anyLong(), any());
        verify(invocationRecordMapper).insert(any(InvocationRecord.class));
    }

    // ---- FAILED 路径 ----

    @Test
    @DisplayName("Case A: ModelInvocationException -> 记录 FAILED/PROVIDER_TIMEOUT，重抛原异常")
    void modelInvocationExceptionShouldRecordFailedWithErrorCode() {

        ModelInvocationException exception = new ModelInvocationException(
                ProxyErrorType.PROVIDER_TIMEOUT);

        when(modelProxyService.invoke(anyLong(), any()))
                .thenThrow(exception);

        ArgumentCaptor<InvocationRecord> recordCaptor =
                ArgumentCaptor.forClass(InvocationRecord.class);

        InvokeRequest request = new InvokeRequest();

        ModelInvocationException thrown = assertThrows(
                ModelInvocationException.class,
                () -> facade.invoke(1L, request));

        assertSame(exception, thrown);

        verify(invocationRecordMapper).insert(recordCaptor.capture());

        InvocationRecord record = recordCaptor.getValue();
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_TIMEOUT", record.getErrorCode());
        assertTrue(record.getDurationMs() >= 0);
        assertNotNull(record.getCreatedAt());
        assertEquals(1L, record.getApplicationId());
    }

    @Test
    @DisplayName("Case B: InvalidProviderResponseException -> FAILED/PROVIDER_INVALID_RESPONSE，原异常透传")
    void invalidProviderResponseExceptionShouldRecordFailed() {

        InvalidProviderResponseException exception =
                new InvalidProviderResponseException("invalid json");

        when(modelProxyService.invoke(anyLong(), any()))
                .thenThrow(exception);

        ArgumentCaptor<InvocationRecord> recordCaptor =
                ArgumentCaptor.forClass(InvocationRecord.class);

        InvokeRequest request = new InvokeRequest();

        InvalidProviderResponseException thrown = assertThrows(
                InvalidProviderResponseException.class,
                () -> facade.invoke(1L, request));

        assertSame(exception, thrown);

        verify(invocationRecordMapper).insert(recordCaptor.capture());

        InvocationRecord record = recordCaptor.getValue();
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("PROVIDER_INVALID_RESPONSE", record.getErrorCode());
    }

    @Test
    @DisplayName("Case C: 其他 RuntimeException -> FAILED/INTERNAL_SERVER_ERROR，原异常透传")
    void otherRuntimeExceptionShouldRecordInternalServerError() {

        RuntimeException exception = new RuntimeException("unexpected");

        when(modelProxyService.invoke(anyLong(), any()))
                .thenThrow(exception);

        ArgumentCaptor<InvocationRecord> recordCaptor =
                ArgumentCaptor.forClass(InvocationRecord.class);

        InvokeRequest request = new InvokeRequest();

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> facade.invoke(1L, request));

        assertSame(exception, thrown);

        verify(invocationRecordMapper).insert(recordCaptor.capture());

        InvocationRecord record = recordCaptor.getValue();
        assertEquals(InvocationStatus.FAILED, record.getStatus());
        assertEquals("INTERNAL_SERVER_ERROR", record.getErrorCode());
    }

    // ---- 保存失败隔离 ----

    @Test
    @DisplayName("Case D: FAILED 保存失败 -> 原模型异常仍然抛出，不过 Mapper 异常")
    void failedRecordSaveFailureShouldStillThrowOriginalException() {

        ModelInvocationException exception = new ModelInvocationException(
                ProxyErrorType.PROVIDER_UNAVAILABLE);

        when(modelProxyService.invoke(anyLong(), any()))
                .thenThrow(exception);

        doThrow(new RuntimeException("db error"))
                .when(invocationRecordMapper).insert(any(InvocationRecord.class));

        InvokeRequest request = new InvokeRequest();

        ModelInvocationException thrown = assertThrows(
                ModelInvocationException.class,
                () -> facade.invoke(1L, request));

        assertSame(exception, thrown);

        verify(modelProxyService, times(1)).invoke(anyLong(), any());
        verify(invocationRecordMapper, times(1)).insert(any(InvocationRecord.class));
    }

    // ---- 最多一次保存 ----

    @Test
    @DisplayName("Case E/F: SUCCESS 路径 insert 失败不会触发 FAILED insert")
    void successInsertFailureShouldNotTriggerFailedInsert() {

        InvokeResponse expectedResponse = new InvokeResponse(
                "content", "model", "stop");

        when(modelProxyService.invoke(anyLong(), any()))
                .thenReturn(expectedResponse);

        doThrow(new RuntimeException("db error"))
                .when(invocationRecordMapper).insert(any(InvocationRecord.class));

        ArgumentCaptor<InvocationRecord> recordCaptor =
                ArgumentCaptor.forClass(InvocationRecord.class);

        InvokeRequest request = new InvokeRequest();
        InvokeResponse actual = facade.invoke(1L, request);

        assertSame(expectedResponse, actual);

        verify(modelProxyService, times(1)).invoke(anyLong(), any());
        verify(invocationRecordMapper, times(1)).insert(recordCaptor.capture());

        InvocationRecord record = recordCaptor.getValue();
        assertEquals(InvocationStatus.SUCCESS, record.getStatus());
        assertNull(record.getErrorCode());
    }

    // ---- FAILED 记录写入失败不重试模型 ----

    @Test
    @DisplayName("ModelInvocationException -> 记录 FAILED，Mapper insert 失败不重试、不吞原异常")
    void modelFailureShouldRecordFailedAndNotRetryModel() {

        ModelInvocationException exception = new ModelInvocationException(
                ProxyErrorType.PROVIDER_TIMEOUT);

        when(modelProxyService.invoke(anyLong(), any()))
                .thenThrow(exception);

        doThrow(new RuntimeException("db error"))
                .when(invocationRecordMapper).insert(any(InvocationRecord.class));

        InvokeRequest request = new InvokeRequest();

        ModelInvocationException thrown = assertThrows(
                ModelInvocationException.class,
                () -> facade.invoke(1L, request));

        assertSame(exception, thrown);
        verify(modelProxyService, times(1)).invoke(anyLong(), any());
        verify(invocationRecordMapper, times(1)).insert(any(InvocationRecord.class));
    }
}