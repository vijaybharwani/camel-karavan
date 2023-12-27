/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";

function Icon({size = 26, fill = 'white'}: {size?: number, fill?: string}) {
    return (
        <svg
        width={size}
        height={size}
        viewBox="0 0 26 23"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M19.3478 11.5963L25.9573 0.474702C26.0142 0.37298 26.0142 0.257695 25.9573 0.155974C25.8933 0.0610331 25.7867 0 25.6659 0H20.9753C20.8545 0 20.7407 0.0610331 20.6839 0.162755L14.117 11.4403C14.0601 11.542 14.0601 11.6573 14.1241 11.759L20.8545 22.7314C20.9113 22.8264 21.025 22.8874 21.1458 22.8874H25.6659C25.7867 22.8874 25.8933 22.8264 25.9573 22.7247C26.0142 22.6229 26.0142 22.5009 25.9573 22.4059L19.3478 11.5963Z"
          fill={fill}
        />
        <path
          d="M5.31876 0.162755C5.2619 0.0610331 5.14819 0 5.02737 0H0.336735C0.215915 0 0.10931 0.0610331 0.0453464 0.155973C-0.0115098 0.250914 -0.0115098 0.37298 0.0453464 0.474702L6.65488 11.5963L0.0453464 22.4127C-0.0115098 22.5144 -0.0186168 22.6365 0.0453464 22.7314C0.102203 22.8332 0.215915 22.8942 0.336735 22.8942H4.8568C4.97762 22.8942 5.08423 22.8332 5.14819 22.7382L11.8785 11.7658C11.9354 11.6709 11.9425 11.5488 11.8857 11.4471L5.31876 0.162755Z"
          fill={fill}
        />
      </svg>
    );
}

export default Icon;
