/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.permissioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.permissioning.NodeWhitelistController.NodesWhitelistResult;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.p2p.P2pDisabledException;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.permissioning.NodeWhitelistController;
import tech.pegasys.pantheon.ethereum.permissioning.WhitelistOperationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PermAddNodesToWhitelistTest {

  private PermAddNodesToWhitelist method;
  private static final String METHOD_NAME = "perm_addNodesToWhitelist";

  private final String enode1 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode2 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String enode3 =
      "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.10:4567";
  private final String badEnode = "enod://dog@cat:fish";

  @Mock private P2PNetwork p2pNetwork;
  @Mock private NodeWhitelistController nodeWhitelistController;

  private JsonRpcParameter params = new JsonRpcParameter();

  @Before
  public void setUp() {
    method = new PermAddNodesToWhitelist(p2pNetwork, params);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(METHOD_NAME);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenOnlyBadEnode() {
    final ArrayList<String> enodeList = Lists.newArrayList(badEnode);
    final JsonRpcRequest request = buildRequest(enodeList);
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_INVALID_ENTRY);

    when(p2pNetwork.getNodeWhitelistController()).thenReturn(Optional.of(nodeWhitelistController));
    when(nodeWhitelistController.addNodes(eq(enodeList))).thenThrow(IllegalArgumentException.class);

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenBadEnodeInList() {
    final ArrayList<String> enodeList = Lists.newArrayList(enode2, badEnode, enode1);
    final JsonRpcRequest request = buildRequest(enodeList);
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_INVALID_ENTRY);

    when(p2pNetwork.getNodeWhitelistController()).thenReturn(Optional.of(nodeWhitelistController));
    when(nodeWhitelistController.addNodes(eq(enodeList))).thenThrow(IllegalArgumentException.class);

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldThrowInvalidJsonRpcParametersExceptionWhenEmptyEnode() {
    final JsonRpcRequest request = buildRequest(Lists.emptyList());
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_EMPTY_ENTRY);

    when(p2pNetwork.getNodeWhitelistController()).thenReturn(Optional.of(nodeWhitelistController));
    when(nodeWhitelistController.addNodes(eq(Lists.emptyList())))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.ERROR_EMPTY_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void whenRequestContainsDuplicatedNodesShouldReturnDuplicatedEntryError() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1, enode1));
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_DUPLICATED_ENTRY);

    when(p2pNetwork.getNodeWhitelistController()).thenReturn(Optional.of(nodeWhitelistController));
    when(nodeWhitelistController.addNodes(any()))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.ERROR_DUPLICATED_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void whenRequestContainsEmptyListOfNodesShouldReturnEmptyEntryError() {
    final JsonRpcRequest request = buildRequest(new ArrayList<>());
    final JsonRpcResponse expected =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NODE_WHITELIST_EMPTY_ENTRY);

    when(p2pNetwork.getNodeWhitelistController()).thenReturn(Optional.of(nodeWhitelistController));
    when(nodeWhitelistController.addNodes(eq(new ArrayList<>())))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.ERROR_EMPTY_ENTRY));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);
  }

  @Test
  public void shouldAddSingleValidNode() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1));
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getId());

    when(p2pNetwork.getNodeWhitelistController()).thenReturn(Optional.of(nodeWhitelistController));
    when(nodeWhitelistController.addNodes(any()))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.SUCCESS));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);

    verify(nodeWhitelistController, times(1)).addNodes(any());
    verifyNoMoreInteractions(nodeWhitelistController);
  }

  @Test
  public void shouldAddMultipleValidNodes() {
    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1, enode2, enode3));
    final JsonRpcResponse expected = new JsonRpcSuccessResponse(request.getId());

    when(p2pNetwork.getNodeWhitelistController()).thenReturn(Optional.of(nodeWhitelistController));
    when(nodeWhitelistController.addNodes(any()))
        .thenReturn(new NodesWhitelistResult(WhitelistOperationResult.SUCCESS));

    final JsonRpcResponse actual = method.response(request);

    assertThat(actual).isEqualToComparingFieldByFieldRecursively(expected);

    verify(nodeWhitelistController, times(1)).addNodes(any());
    verifyNoMoreInteractions(nodeWhitelistController);
  }

  @Test
  public void shouldFailWhenP2pDisabled() {
    when(p2pNetwork.getNodeWhitelistController())
        .thenThrow(new P2pDisabledException("P2P disabled."));

    final JsonRpcRequest request = buildRequest(Lists.newArrayList(enode1, enode2, enode3));
    ;
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.P2P_DISABLED);

    assertThat(method.response(request)).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest buildRequest(final List<String> enodeList) {
    return new JsonRpcRequest("2.0", METHOD_NAME, new Object[] {enodeList});
  }
}
