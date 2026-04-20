---
name: sync-expert-api-webapp
description: 本技能主要是对专家API同步到webapp下expert目录下的控制器，webapp作为代理控制器，提供统一的访问接口，禁止修改专家接口的出参和入参，只能对webapp进行改造。
---
# 意图识别
- 必须先询问用户给出同步的专家项目目录和webapp的目录，然后继续读禁止的行为和改造逻辑


# 禁止的行为，请参考如下
- 禁止修改专家API的出参和入参，只能对webapp进行改造。
- 禁止修改专家API的逻辑，请只对webapp进行改造。
- 只同步专家新增的api和修改的api，主要的控制器在`DigitalExpertController`和`AiAgentController`，请勿同步其他控制器。
- 现有的webapp的api逻辑不要修改，如果参数有新增或者变动同步即可

# webapp项目的改造，请参考如下
- 遵循其现有的编码风格，尽量做到复用

