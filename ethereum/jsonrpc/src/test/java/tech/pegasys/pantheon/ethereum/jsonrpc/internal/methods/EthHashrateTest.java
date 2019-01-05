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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthHashrateTest {

  @Mock private EthHashMiningCoordinator miningCoordinator;
  private EthHashrate method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_hashrate";

  @Before
  public void setUp() {
    method = new EthHashrate(miningCoordinator);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnValueFromMiningCoordinator() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0xc");
    when(miningCoordinator.hashesPerSecond()).thenReturn(Optional.of(12L));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenMiningCoordinatorDoesNotHaveHashes() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.NO_HASHES_PER_SECOND);
    when(miningCoordinator.hashesPerSecond()).thenReturn(Optional.empty());

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnErrorWhenMiningCoordinatorDoesNotSupportHashing() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_REQUEST);
    when(miningCoordinator.hashesPerSecond()).thenThrow(UnsupportedOperationException.class);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest requestWithParams() {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, new Object[] {});
  }
}