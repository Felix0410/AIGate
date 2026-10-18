package com.felix.aigate.invocation;

import com.felix.aigate.invocation.entity.InvocationRecord;
import com.felix.aigate.invocation.mapper.InvocationRecordMapper;
import com.felix.aigate.invocation.service.InvocationTrackingFacade;
import com.felix.aigate.runtime.dto.InvokeRequest;
import com.felix.aigate.runtime.dto.InvokeResponse;
import com.felix.aigate.runtime.service.ModelInvocationException;
import com.felix.aigate.runtime.service.ModelProxyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("ModelProxyService 失败：原异常向上传播，不尝试写入记录")
    void modelFailureShouldPropagateAndNotInsertRecord() {

        ModelInvocationException exception = new ModelInvocationException(
                com.felix.aigate.runtime.exception.ProxyErrorType.PROVIDER_TIMEOUT);

        when(modelProxyService.invoke(anyLong(), any()))
                .thenThrow(exception);

        InvokeRequest request = new InvokeRequest();

        ModelInvocationException thrown = assertThrows(
                ModelInvocationException.class,
                () -> facade.invoke(1L, request));

        assertSame(exception, thrown);
        verify(invocationRecordMapper, never()).insert(any(InvocationRecord.class));
    }
}