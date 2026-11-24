# Baseline luật (RuleIntentClassifier) — val

### (a) 12 nhãn (nhãn luật không có → luật trả OTHER)

n = 154  |  accuracy = 0.2403  |  macro-F1 = 0.2628

EMERGENCY: recall = 0.5000 (10/20), precision = 0.9091, báo nhầm (FP) = 1

| nhãn | precision | recall | F1 | support |
|---|---:|---:|---:|---:|
| BOOKING | 0.000 | 0.000 | 0.000 | 15 |
| CARE_ADVICE | 0.000 | 0.000 | 0.000 | 20 |
| CONTACT | 1.000 | 0.455 | 0.625 | 11 |
| EMERGENCY | 0.909 | 0.500 | 0.645 | 20 |
| GREETING | 1.000 | 0.143 | 0.250 | 7 |
| OFF_TOPIC | 1.000 | 0.300 | 0.462 | 10 |
| OPENING_HOURS | 1.000 | 0.250 | 0.400 | 12 |
| OTHER | 0.094 | 1.000 | 0.171 | 12 |
| PRICE | 0.000 | 0.000 | 0.000 | 13 |
| PRODUCT | 0.000 | 0.000 | 0.000 | 15 |
| SERVICE_INFO | 0.000 | 0.000 | 0.000 | 12 |
| THANKS | 1.000 | 0.429 | 0.600 | 7 |

Ma trận nhầm lẫn (hàng = thật, cột = dự đoán):

```
thật\dự        BOOKIN CARE_A CONTAC EMERGE GREETI OFF_TO OPENIN  OTHER  PRICE PRODUC SERVIC THANKS
BOOKING             0      0      0      0      0      0      0     15      0      0      0      0
CARE_ADVICE         0      0      0      0      0      0      0     20      0      0      0      0
CONTACT             0      0      5      0      0      0      0      6      0      0      0      0
EMERGENCY           0      0      0     10      0      0      0     10      0      0      0      0
GREETING            0      0      0      0      1      0      0      6      0      0      0      0
OFF_TOPIC           0      0      0      0      0      3      0      7      0      0      0      0
OPENING_HOURS       0      0      0      0      0      0      3      9      0      0      0      0
OTHER               0      0      0      0      0      0      0     12      0      0      0      0
PRICE               0      0      0      0      0      0      0     13      0      0      0      0
PRODUCT             0      0      0      0      0      0      0     15      0      0      0      0
SERVICE_INFO        0      0      0      1      0      0      0     11      0      0      0      0
THANKS              0      0      0      0      0      0      0      4      0      0      0      3
```

### (b) 7 nhãn của luật (PRICE/SERVICE_INFO/PRODUCT/BOOKING/CARE_ADVICE → OTHER)

n = 154  |  accuracy = 0.7208  |  macro-F1 = 0.5402

EMERGENCY: recall = 0.5000 (10/20), precision = 0.9091, báo nhầm (FP) = 1

| nhãn | precision | recall | F1 | support |
|---|---:|---:|---:|---:|
| EMERGENCY | 0.909 | 0.500 | 0.645 | 20 |
| OFF_TOPIC | 1.000 | 0.300 | 0.462 | 10 |
| GREETING | 1.000 | 0.143 | 0.250 | 7 |
| THANKS | 1.000 | 0.429 | 0.600 | 7 |
| CONTACT | 1.000 | 0.455 | 0.625 | 11 |
| OPENING_HOURS | 1.000 | 0.250 | 0.400 | 12 |
| OTHER | 0.672 | 0.989 | 0.800 | 87 |

Ma trận nhầm lẫn (hàng = thật, cột = dự đoán):

```
thật\dự        EMERGE OFF_TO GREETI THANKS CONTAC OPENIN  OTHER
EMERGENCY          10      0      0      0      0      0     10
OFF_TOPIC           0      3      0      0      0      0      7
GREETING            0      0      1      0      0      0      6
THANKS              0      0      0      3      0      0      4
CONTACT             0      0      0      0      5      0      6
OPENING_HOURS       0      0      0      0      0      3      9
OTHER               1      0      0      0      0      0     86
```
