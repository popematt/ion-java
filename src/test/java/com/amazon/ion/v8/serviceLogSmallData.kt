package com.amazon.ion.v8

object ServiceLogSmallData {

    val ivm = """
    E0 01 01 EA
    """

    val symTab0 = """
    E1                                  | (:$ set_symbols
    92 6D 73                            |    "ms"
    91 42                               |    "B"
    91 73                               |    "s"
    94 4e 61 6d 65                      |    "Name"
    97 53 61 6d 70 6c 65 73             |    "Samples"
    94 55 6e 69 74                      |    "Unit"
    95 43 6f 75 6e 74                   |    "Count"
    9A 44 69 6d 65 6e 73 69 6f 6e 73    |    "Dimensions"
    93 53 75 6d                         |    "Sum"
    95 56 61 6c 75 65                   |    "Value"
    96 52 65 70 65 61 74                |    "Repeat"
    99 4f 70 65 72 61 74 69 6f 6e       |    "Operation"
    F0                                  | )
    """


    val macTab0 = """
    E3                                  | (:$ set_macros
    F2                                  |   (
    A3 6F 6E 65                         |     one
    FA 01                               |     ''
    F0                                  |   )
    F2                                  |   (
    a5 65 6e 74 72 79                   |     entry
    F3                                  |     {
    ED 53 74 61 72 74 54 69 6D 65   E9  |       StartTime: (:?),
    F1 45 6e 64 54 69 6d 65         E9  |       EndTime: (:?),
    E9 4d 61 72 6b 65 74 70
       6c 61 63 65                  E9  |       Marketplace: (:?),
    F1 50 72 6f 67 72 61 6d         E9  |       Program: (:?),
    f7 54 69 6d 65                  E9  |       Time: (:?),
    19                              E9  |       Operation: (:?),
    eb 50 72 6f 70 65 72 74
       69 65 73                     E9  |       Properties: (:?),
    e3 53 65 72 76 69 63 65
       4d 65 74 72 69 63 73         E9  |       ServiceMetrics: (:?),
    f3 54 69 6d 69 6e 67            E9  |       Timing: (:?),
    ef 43 6f 75 6e 74 65 72 73      E9  |       Counters: (:?),
    f3 4c 65 76 65 6c 73            E9  |       Levels: (:?),
    f1 4d 65 74 72 69 63 73         E9  |       Metrics: (:?),
    f3 47 72 6f 75 70 73            E9  |       Groups: (:?),
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a7 73 75 6d 6d 61 72 79             |     summary
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                              E9  |       Sum: (:?),
    0D                                  |       Unit:
       EA    FA 01                      |             (:? ''),
    0F                                  |       Count:
       EA    61 01                      |              (:? 1),
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a5 67 72 6f 75 70                   |     group
    F3                                  |     {
    e9 53 65 72 76 69 63 65             |
       4e 61 6d 65                  E9  |       ServiceName: (:?),
    19                              E9  |       Operation: (:?),
    eb 41 74 74 72 69 62 75 74 65 73 E9 |       Attributes: (:?),
    f3 54 69 6d 69 6e 67            E9  |       Timing: (:?),
    ef 43 6f 75 6e 74 65 72 73      E9  |       Counters: (:?),
    f3 4c 65 76 65 6c 73            E9  |       Levels: (:?),
    01                                  |       $ 0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a6 73 61 6d 70 6c 65                |     sample
    F3                                  |     {
    15                              E9  |       Value: (:?),
    17                        EA 61 01  |       Repeat: (:? 1),
    01                                  |       $ 0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a9 64 69 6d 65 6e 73 69 6f 6e       |     dimension
    F3                                  |     {
    f5 43 6c 61 73 73               E9  |       Class: (:?),
    ef 49 6e 73 74 61 6e 63 65      E9  |       Instance: (:?),
    01                                  |       $ 0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a6 6d 65 74 72 69 63                |     metric
    F3                                  |     {
    09                              E9  |       Name: (:?),
    0B                              E9  |       Samples: (:?),
    0D                                  |       Unit:
       EA    FA 01                      |             (:? ''),
    11                               E9 |       Dimensions: (:?),
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    ad 6d 65 74 72 69 63 5f
       73 69 6e 67 6c 65                |     metric_single
    F3                                  |     {
    09                              E9  |       Name: (:?),
    0B                                  |       Samples:
    F1                                  |         [
    F3                                  |           {
    15                              E9  |             Value: (:?),
    17                        EA 61 01  |             Repeat: (:? 1),
    01                                  |             $ 0: <END-IMPLIED-NOP>
    F0                                  |           }
    F0                                  |       ],
    0D                                  |       Unit:
       EA    FA 01                      |             (:? ''),
    11                               E9 |       Dimensions: (:?),
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    ab 73 75 6d 6d 61 72 79 5f 4f 4e 45 |     summary_ONE
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                              E9  |       Sum: (:?),
    0D                           FA 01  |       Unit: '',
    0F                              E9  |       Count: (:?),
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    aa 73 75 6d 6d 61 72 79 5f 6d 73    |     summary_ms
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                              E9  |       Sum: (:?),
    0D                           A0 03  |       Unit: ms,
    0F                                  |       Count:
       EA    61 01                      |              (:? 1),
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a9 73 75 6d 6d 61 72 79 5f 42       |     summary_B
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                              E9  |       Sum: (:?),
    0D                           A0 05  |       Unit: B,
    0F                                  |       Count:
       EA    61 01                      |              (:? 1),
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a9 73 75 6d 6d 61 72 79 5f 30       |     summary_0
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                              6A  |       Sum: 0e0,
    0D                           FA 01  |       Unit: '',
    0F                           61 01  |       Count: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a9 73 75 6d 6d 61 72 79 5f 31       |     summary_1
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                  6C 00 00 80 3F  |       Sum: 1e0,
    0D                           FA 01  |       Unit: '',
    0F                           61 01  |       Count: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a9 73 75 6d 6d 61 72 79 5f 32       |     summary_2
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                  6C 00 00 00 40  |       Sum: 2e0,
    0D                           FA 01  |       Unit: '',
    0F                           61 01  |       Count: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a9 73 75 6d 6d 61 72 79 5f 33       |     summary_3
    F3                                  |     {
    09                              E9  |       Name: (:?),
    13                  6C 00 00 40 40  |       Sum: 3e0,
    0D                           FA 01  |       Unit: '',
    0F                           61 01  |       Count: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a7 73 61 6d 70 6c 65 30             |     sample0
    F3                                  |     {
    15                              6A  |       Value: 0e0,
    17                           61 01  |       Repeat: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a7 73 61 6d 70 6c 65 31             |     sample1
    F3                                  |     {
    15                  6C 00 00 80 3F  |       Value: 1e0,
    17                           61 01  |       Repeat: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a7 73 61 6d 70 6c 65 32             |     sample2
    F3                                  |     {
    15                  6C 00 00 00 40  |       Value: 2e0,
    17                           61 01  |       Repeat: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F2                                  |   (
    a7 73 61 6d 70 6c 65 33             |     sample3
    F3                                  |     {
    15                  6C 00 00 40 40  |       Value: 3e0,
    17                           61 01  |       Repeat: 1,
    01                                  |       $0: <END-IMPLIED-NOP>
    F0                                  |     }
    F0                                  |   )
    F0                                  | )

    """

    val symTab1 = """

    E1                                  | (:$ set_symbols
    92 6D 73                            |    "ms"
    91 42                               |    "B"
    91 73                               |    "s"
    99 75 73 2d 65 61 73 74             | · · "us-east-1"
    2d 31                               | · ·
    f9 37 4c 61 6d 62 64 61             | · · "LambdaFrontendInvokeService"
    46 72 6f 6e 74 65 6e 64             | · ·
    49 6e 76 6f 6b 65 53 65             | · ·
    72 76 69 63 65                      | · ·
    9f 52 65 73 65 72 76 65             | · · "ReserveSandbox2"
    53 61 6e 64 62 6f 78 32             | · ·
    f9 25 46 72 6f 6e 74 65             | · · "FrontendInstanceId"
    6e 64 49 6e 73 74 61 6e             | · ·
    63 65 49 64                         | · ·
    f9 27 69 2d 30 35 30 35             | · · "i-0505be8aa9972815b"
    62 65 38 61 61 39 39 37             | · ·
    32 38 31 35 62                      | · ·
    99 41 63 63 6f 75 6e 74             | · · "AccountId"
    49 64                               | · ·
    9c 31 30 33 34 30 33 39             | · · "103403959176"
    35 39 31 37 36                      | · ·
    99 52 65 71 75 65 73 74             | · · "RequestId"
    49 64                               | · ·
    f9 49 66 30 62 63 33 32             | · · "f0bc3259-06e9-5ccb-96f1-6a44af76d4aa"
    35 39 2d 30 36 65 39 2d             | · ·
    35 63 63 62 2d 39 36 66             | · ·
    31 2d 36 61 34 34 61 66             | · ·
    37 36 64 34 61 61                   | · ·
    93 50 49 44                         | · · "PID"
    f9 27 32 38 31 32 40 69             | · · "2812@ip-10-0-16-227"
    70 2d 31 30 2d 30 2d 31             | · ·
    36 2d 32 32 37                      | · ·
    98 57 6f 72 6b 65 72 49             | · · "WorkerId"
    64                                  | · ·
    f9 27 69 2d 30 63 38 39             | · · "i-0c891b196c563ba4c"
    31 62 31 39 36 63 35 36             | · ·
    33 62 61 34 63                      | · ·
    f9 25 46 72 6f 6e 74 65             | · · "FrontendInternalAZ"
    6e 64 49 6e 74 65 72 6e             | · ·
    61 6c 41 5a                         | · ·
    95 55 53 4d 41 37                   | · · "USMA7"
    f9 2f 57 6f 72 6b 65 72             | · · "WorkerManagerInstanceId"
    4d 61 6e 61 67 65 72 49             | · ·
    6e 73 74 61 6e 63 65 49             | · ·
    64                                  | · ·
    f9 27 69 2d 30 37 30 62             | · · "i-070b7692b9a6aba7e"
    37 36 39 32 62 39 61 36             | · ·
    61 62 61 37 65                      | · ·
    99 53 61 6e 64 62 6f 78             | · · "SandboxId"
    49 64                               | · ·
    f9 49 36 31 66 61 34 63             | · · "61fa4c30-d51d-40bb-82e0-a6195e27ca10"
    33 30 2d 64 35 31 64 2d             | · ·
    34 30 62 62 2d 38 32 65             | · ·
    30 2d 61 36 31 39 35 65             | · ·
    32 37 63 61 31 30                   | · ·
    96 54 68 72 65 61 64                | · · "Thread"
    f9 2d 63 6f 72 61 6c 2d             | · · "coral-orchestrator-136"
    6f 72 63 68 65 73 74 72             | · ·
    61 74 6f 72 2d 31 33 36             | · ·
    f9 21 46 72 6f 6e 74 65             | · · "FrontendPublicAZ"
    6e 64 50 75 62 6c 69 63             | · ·
    41 5a                               | · ·
    9a 75 73 2d 65 61 73 74             | · · "us-east-1a"
    2d 31 61                            | · ·
    f9 23 57 6f 72 6b 65 72             | · · "WorkerConnectPort"
    43 6f 6e 6e 65 63 74 50             | · ·
    6f 72 74                            | · ·
    94 32 35 30 33                      | · · "2503"
    99 54 69 6d 65 3a 57 61             | · · "Time:Warm"
    72 6d                               | · ·
    97 41 74 74 65 6d 70 74             | · · "Attempt"
    97 53 75 63 63 65 73 73             | · · "Success"
    95 45 72 72 6f 72                   | · · "Error"
    95 46 61 75 6c 74                   | · · "Fault"
    F0                                  | )
    """

    val tlv0 = """
    01                                  | (:entry
    85 b2 2d 47 ba 23 0f                |   2020-11-05T07:18:59.968+00:00 // start_time
    84 b2 2d 47 ba 03                   |   2020-11-05T07:18:59+00:00 // end_time
    A0 09                               |   'us-east-1' <$4>
    A0 0B                               |   LambdaFrontendInvokeService <$5>
    6c 24 67 47 4a                      |   3.267017e6
    A0 0D                               |   ReserveSandbox2 // <$6>
    f3                                  | · {
    0f                                  | · · FrontendInstanceId:  // <$7>
    A0 11                               | · · 'i-0505be8aa9972815b', // <$8>
    13                                  | · · AccountId:  // <$9>
    A0 15                               | · · '103403959176', // <$10>
    17                                  | · · RequestId:  // <$11>
    A0 19                               | · · 'f0bc3259-06e9-5ccb-96f1-6a44af76d4aa', // <$12>
    1b                                  | · · PID:  // <$13>
    A0 1d                               | · · '2812@ip-10-0-16-227', // <$14>
    1f                                  | · · WorkerId:  // <$15>
    A0 21                               | · · 'i-0c891b196c563ba4c', // <$16>
    23                                  | · · FrontendInternalAZ:  // <$17>
    A0 25                               | · · USMA7, // <$18>
    27                                  | · · WorkerManagerInstanceId:  // <$19>
    A0 29                               | · · 'i-070b7692b9a6aba7e', // <$20>
    2b                                  | · · SandboxId:  // <$21>
    A0 2D                               | · · '61fa4c30-d51d-40bb-82e0-a6195e27ca10', // <$22>
    2f                                  | · · Thread:  // <$23>
    A0 31                               | · · 'coral-orchestrator-136', // <$24>
    33                                  | · · FrontendPublicAZ:  // <$25>
    A0 35                               | · · 'us-east-1a', // <$26>
    37                                  | · · WorkerConnectPort:  // <$27>
    A0 39                               | · · '2503', // <$28>
    01                                  |     $0: <NOP/END>
    f0                                  | · } // properties
    f1                                  | · [
    09                                  | · · (:summary_ms
    A0 3B                               | · · · 'Time:Warm' // <$29> name
    6c f8 1a 51 40                      | · · · 3.267271041870117e0 // value
    E8                                  | · · · (:nothing) // count
                                        | · · ),
    f0                                  | · ] // timing
    f1                                  | · [
    0b                                  | · · (:summary0
    A0 3D                               | · · · Attempt // <$30> name
                                        | · · ),
    0c                                  | · · (:summary1
    A0 3F                               | · · · Success // <$31> name
                                        | · · ),
    f0                                  | · ] // counters
    e8                                  | · (:none) // levels
    e8                                  | · (:none) // service_metrics
    f1                                  | · [
    07                                  | · · (:metric_single
    A0 41                               | · · · Error // <$32> name
    6a                                  | · · · 0e0 // value
    e8                                  | · · · (::) // repeat
    e8                                  | · · · (::) // unit
    e8                                  | · · · (::) // dimensions
                                        | · · ),
    07                                  | · · (:metric_single
    A0 43                               | · · · Fault // <$33> name
    6a                                  | · · · 0e0 // value
    e8                                  | · · · (::) // repeat
    e8                                  | · · · (::) // unit
    e8                                  | · · · (::) // dimensions
                                        | · · ),
    f0                                  | · ] // metrics
    e8                                  | · (:none) // groups
                                        | )
    """

    val symTab2 = """
    e2                                  | (:add_symbols
    f9 25 57 53 4b 46 3a 47             | ·
       65 74 4c 61 74 65 73             | ·
       74 4b 65 79 73                   | · "WSKF:GetLatestKeys"
    97 4c 61 74 65 6e 63 79             | · "Latency"
    f0                                  | )
    """

    val tlv1 = """
    01                                  | (:entry
    85 b2 2d 47 ba 2f 0f                | · 2020-11-05T07:18:59.971+00:00 // start_time
    84 b2 2d 47 ba 03                   | · 2020-11-05T07:18:59+00:00 // end_time
    A0 09                               | · 'us-east-1' // <$4> marketplace
    A0 0B                               | · LambdaFrontendInvokeService // <$5> program
    6c 00 80 3b 45                      | · 3e3 // time
    A0 45                               | · 'WSKF:GetLatestKeys' // <$34> operation
    f3                                  | · {
    0f                                  | · · FrontendInstanceId:  // <$7>
    A0 11                               | · · 'i-0505be8aa9972815b', // <$8>
    33                                  | · · FrontendPublicAZ:  // <$25>
    A0 35                               | · · 'us-east-1a', // <$26>
    1b                                  | · · PID:  // <$13>
    A0 1D                               | · · '2812@ip-10-0-16-227', // <$14>
    23                                  | · · FrontendInternalAZ:  // <$17>
    A0 25                               | · · USMA7, // <$18>
    01                                  |     $0: <NOP/END>
    f0                                  | · } // properties
    f1                                  | · [
    09                                  | · · (:summary_ms
    A0 47                               | · · · Latency // <$35> name
    6c 6f 12 03 3b                      | · · · 2.0000000949949026e-3 // value
    E8                                  | · · · (:nothing) // count
                                        | · · ),
    f0                                  | · ] // timing
    e8                                  | · (:nothing) // counters
    e8                                  | · (:nothing) // levels
    e8                                  | · (:nothing) // service_metrics
    e8                                  | · (:nothing) // metrics
    e8                                  | · (:nothing) // groups
                                        | )
"""

    val serviceLogDataSmall = ivm + symTab0 + macTab0 + symTab1 + tlv0 + symTab2 + tlv1
}
