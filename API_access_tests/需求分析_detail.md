# 需求分析

## 1.执行摘要

&nbsp;&nbsp;本项目目标是开发一款辅助拍照移动应用。传统的摄影学习曲线陡峭，而现有的辅助工具多集中在后期修图，无法解决拍摄时“视角”和“构图”的根本问题。受Google Pixel系列手机“Camera Coach”功能 以及最新学术成果PhotoFramer的启发，本项目致力于构建一个**实时摄影指导系统**来指导摄影小白进行拍摄。

&nbsp;&nbsp;该系统不仅能“看懂”画面，还能像一位就在身边的摄影导师一样，通过直观的视觉引导，实时指挥用户移动相机位置、调整角度或改变焦距，从而在按下快门前获得完美的构图。

&nbsp;&nbsp;开发大致分为三个阶段：使用开源/闭源API进行功能验证并搭建基础流程，替换现有API为自主微调模型，产品化尝试。

## 2.市场背景与竞品调研

### 2.1相关产品

#### 2.1.1Camera Coach

Google Pixel手机的Camera Coach是本项目的直接对标对象。其核心流程为：

- 点击右上角图标，显示分析中画面，分析完毕后，展示候选方案：

- 点击候选方案之后，开始构图指导，分为几个阶段（猜测是模型直接生成几个阶段，然后本地判断是否完成指令）

- 构图指导完成之后，显示take the photo。

  <img src="需求分析_detail.assets/image-20260102085421480.png" alt="image-20260102085421480" style="zoom:50%;" />

  ==难点==：

  - ~~带有简要文字的参考图:目前找的模型多为单纯的图像生成，无法同时生成文字~~ nano banana 的返回内容可以是图片+文本。
  
  - 拍摄模式切换：可能需要根据具体手机型号进行适配
  
  - 指令的实时判断：一直调用大模型进行判断？or模型给出具体的姿态调整方案（文字描述+具体指令和幅度，然
  
    后使用简单的图像位置判断？）
    
  - 同时生成3-4张图片+文本描述，而当前的图生图模型一般只能生成一个。

#### 2.1.2Doka相机

整体上感觉Doka相机是模仿的华为的拍照，核心功能只有两点：给出一个聚焦位置，让用户对齐，然后变焦，获得图片之后，推荐滤镜。

- 分析画面
- 给出聚焦位置（一个环，这个环貌似是在画面中固定位置的，随着视角变动，环还是在旗的一个地方，从视频中来看，十字没完全对准后面偏了的环，而是对准之前的位置之后 自动变焦了）
- 变焦：应该是先裁切，然后根据子图和原图的比例判断 变焦倍数
- 滤镜推荐：==看样子有很多滤镜,貌似是他们的一个核心优势？==
  <img src="需求分析_detail.assets/image-20260102094051996.png" alt="image-20260102094051996" style="zoom: 25%;" align="left" /><img src="需求分析_detail.assets/image-20260102094233047.png" alt="image-20260102094233047" style="zoom:25%;" /><img src="需求分析_detail.assets/image-20260102094340289.png" alt="image-20260102094340289" style="zoom:25%;" />

#### 2.1.3浅影AI相机

根据场景推荐模板，没见到实物视频，可能噱头大于实用。下图没有模特，但是还提示模特位置了。

<img src="需求分析_detail.assets/image-20260102095554941.png" alt="image-20260102095554941" style="zoom: 20%;" /><img src="需求分析_detail.assets/image-20260102095630610.png" alt="image-20260102095630610" style="zoom:20%;" />

<img src="需求分析_detail.assets/image-20260102095711748.png" alt="image-20260102095711748" style="zoom:20%;" align="left" />

#### 2.1.4可颂

与浅影AI相机差不多，都是依赖于模板的，不做过多展示

---

### 2.2相关论文

#### 2.2PhotoFramer

《PhotoFramer: Multi-modal Image Composition Instruction》 为本项目提供了坚实的算法理论基础。与传统的图像美学评分（Aesthetic Scoring）不同，PhotoFramer 提出了一种生成式的指导范式。

| 特性         | 传统构图模型                          | PhotoFramer (本项目参考架构)                                 |
| ------------ | ------------------------------------- | ------------------------------------------------------------ |
| **输出形式** | 评分 (Score) 或 裁剪框 (Bounding Box) | **自然语言指令 (Text) + 目标参考图 (Example Image)**         |
| **任务类型** | 仅支持裁剪 (Crop)                     | **位移 (Shift)**、**变焦 (Zoom-in)**、**视点改变 (View-change)** |
| **交互逻辑** | 被动式：告诉你这张照片好不好          | **主动式：告诉你如何移动相机变好**                           |
| **数据来源** | 静态美学数据集 (AVA, GAIC)            | 构图数据集 + **降质生成的合成数据 (Degradation Model)**      |

PhotoFramer 将构图任务分层为“Shift”（平移）、“Zoom-in”（缩放）和“View-change”（视角变换）三个子任务 。这一分层结构直接决定了我们APP的功能模块划分。特别是“View-change”任务，它要求模型能够“脑补”出当前视角之外的画面（Outpainting）或从未拍摄的角度，这是实现“摄影导师”即视感的关键。

## 3用户画像与需求场景分析

### 3.1目标用户：影像旗舰机用户&帮对象拍照的人

- 影像旗舰用户：摄影爱好者，没有专业的器材，也没经过系统的学习，这类用户在选购手机时往往选择全能的影像旗舰。 年龄大概在20-40岁之间。
- 帮对象拍照的人：主要为帮女朋友拍照的男生（也有帮男朋友拍照的），大部分男生拍照只是为了记录，而非出片，在“主动”给女朋友拍照时往往缺乏构图经验而无法拍出令人满意的照片。

### 3.2 核心痛点

- 对于影像旗舰用户，3A系统已经被手机厂商做的很好了，缺乏的是好的构图，即面对风景或人像，不知道拍哪里，怎么拍。
- 对于帮女朋友拍照的男生，构图是一个核心痛点，另一个痛点是模特的姿势的指导。即只会听女朋友说要怎么拍，但是对于具体的构图细节&模特的姿势，并没有独到的见解。

## 4需求规格说明书

### 4.1 核心功能需求

#### 4.1.1 核心功能一：智能构图

- **描述**：用户点击构图分析之后，系统能够识别和分析当前画面，生成优化后的“目标图像”，以及对目标图像的简单描述。 用户选择目标图像之后，系统给出文字指令，指导用户进行调整，最终当用户调整到目标的构图之后，系统指导用户按下快门。

- **输入：**当前相机预览帧（构图分析阶段），当前相机预览帧以及优化后的目标构图（指令生成阶段）。

- **输出：**

  - 优化后的目标构图：3-5张AI给出的理想构图参考图
  - 操作指令：包含简短的构图指令以及 稍长一些的人像动作指导指令。
  - 自然语言解释：解释指令，如“向右移动以移除左侧杂乱的树枝，使主体更加突出”。

- ==技术支撑&实现逻辑：==

  第一阶段调用开源的API，如Qwen系列和Gemini的进行如下任务：

  - 图生图：用于生成参考图以及简单的文本描述。
    prompt参见photo Framer里的

  - 图片推理：用于分析参考图和用户上传的取景器画面，来生成指令以及自然语言解释。
    标准化输出数据格式：

    ```json
    {
      "指令1":{
        "具体幅度":"哪种操作+百分数/多少度",
        "原因"："描述为何执行这一步操作，规定字数！！"
      },
      "指令2":{
           ...
    	},
      ...
    }
    ```

  - 第二阶段：微调多模态大模型，使用photo framer等模型，来同时生成图片和文字指令。

  > [!NOTE]
  >
  > 操作指令的范围: 
  >
  > ​                 高优先级：shift，zoom-in，view-change。
  >
  > ​                 低优先级：人物动作指导指令

#### 4.1.2 核心功能二：视觉引导系统

- 描述：系统的核心交互创新，需实时计算 当前画面的调整是否满足 大模型给出的指令以及幅度，并通过ui来引导用户将当前画面与大模型给出的参考画面进行对齐。
- 实现逻辑：
  - **特征匹配**：提取当前帧与目标图像的特征点（ORB/SIFT）。
  - **单应性矩阵计算（Homography）**：计算两个视角间的变换矩阵 $H$ 。
  - **位姿解算**：将矩阵 $H$ 分解为旋转（Rotation）和平移（Translation）向量，判断相机移动方向是否满足大模型返回的幅度。
- UI表现
  - 参考图呈现：采用幽灵层/左下角悬浮等样式，将参考图叠加到取景器上。
  - 动态箭头：根据位姿解算结果，在屏幕上显示箭头，指示移动方向。

#### 4.1.3 核心功能三：智能快门/相机控制

- **描述**：当用户根据引导调整到位，当前画面与目标图像的相似度（IoU或特征匹配数）超过阈值（如85%）时，系统自动触发快门或给予强烈的视觉/触觉反馈（边框变绿、震动）。
- 实现逻辑：需要调用android/iOS对于相机的接口来实现变焦等操作。

### 4.2 非功能需求

#### 4.2.1实时性与延迟

- **相机预览**：必须保持在30fps以上，否则会产生眩晕感，影响构图体验 。  
- **云端分析**：允许3-5秒的等待时间（用户按下“分析”按钮后），期间需通过UI动效（Marquee Effect）缓解焦虑 。  
- **本地引导**：视觉伺服计算（特征匹配+UI绘制）必须在50ms内完成（即达到20fps以上），以保证引导的流畅性 。  

#### 4.2.2鲁棒性

- **弱纹理场景**：在白墙或蓝天等特征点稀少的场景，基于特征匹配的视觉引导可能失效。系统需具备降级策略，利用手机IMU（陀螺仪、加速度计）进行纯传感器的姿态估计 

### 4.3 未来可能的拓展需求

**<span  style="font-size:1.2em">基于人体骨骼的姿态调整</span>**

- **描述**：针对人像摄影，系统需识别人物骨骼，并与内置的“完美姿态库”进行比对。
- **技术支撑**：集成Google MediaPipe Pose，实时追踪33个身体关键点 。  
- **交互**：在屏幕上绘制虚拟骨骼线，若用户姿态不准（如手肘下垂），对应骨骼线显示红色，调整正确后变绿。

## 5分阶段技术实施方案

以下技术方案仅为大致方向，实际方向可能因构建过程中遇到的问题而调整。

### 5.1 第一阶段：基于现有API的原型

**目标**：快速验证“AI分析 -> 生成目标图 -> 引导拍摄”这一产品闭环的体验，不纠结于模型训练，重点在于**全链路跑通**和**交互逻辑实现**。

#### 5.1.1核心架构：边云协同

- 云端：负责参考图像生成以及指令生成
- 端侧：负责高频的相机预览、特征追踪和UI渲染。

#### 5.1.2 关键技术方案

<span style="font-size:1.2em">**1.参考图像生成的提示词**</span>

Photo Framer原文提到 自动任务指令比明确任务指令要好，所以此处暂时选用自动任务指令的提示词（模型自主选择使用哪种操作）

- **task Prompt of to auto Task(shift and zoom-in)**

  - Refine the composition through shift or zoom-in adjustments. 

  - Improve the composition by combining shift or zoom-in operations.

  - Enhance the image composition with coordinated shift or zoom-in refinement. 

  - Adjust the framing through shift or zoom-in to achieve a better composition. 

  - Make the composition more appealing using shift or zoom-in adjustments. 

  - Refine the scene composition with gentle shift or zoom-in movement. 

  - Improve the framing by applying continuous shift or zoom-in optimization. 

  - Enhance the overall composition through integrated shift or zoom-in refinement. 

  - Adjust the frame smoothly using shift or zoom-in to strengthen composition. 

  - Refine the image framing through natural shift or zoom-in enhancement

- **Prompt for Full Auto Task (All Three Tasks)**

  - Capture this scene with better composition. 

  - Enhance the composition of this scene. 

  - Refine the composition to make this scene more visually pleasing. 

  - Adjust the image to improve its composition. 

  - Reframe this scene to achieve a more pleasing visual composition. 

  - Refine the framing through shift, zoom-in, or view change. 

  - Improve the composition through shift, zoom-in, or viewpoint adjustment. 

  - Enhance the image composition through shift, zoom-in, and view change. 

  - Optimize the composition through shift, zoom-in, or new viewpoint exploration. 

  - Refine the scene composition through shift, zoom-in, or view change as needed.

*参考效果*

<img src="需求分析_detail.assets/image-20260101162935974.png" alt="image-20260101162935974" style="zoom:40%;" align="left"/><img src="需求分析_detail.assets/image-20260101163013714.png" alt="image-20260101163013714" style="zoom:30%;"/>



<span style="font-size:1.2em">**2.用户操作指令生成的提示词**</span>

你是一个出色的构图分析师，你擅长分析图片的差异，然后给出具体的指令来指导用户对原图进行调整，以达到优化后的构图，你可选的指令有 shift Zoom-in view-change。需要的指令数目和每条指令的幅度需要你自己判断。你的输出需要满足json格式，{"指令1":{"幅度","原因"，"指令2":{"幅度","原因"}。其中幅度为具体数值以供用户实施具体的操作。

==怎么告诉模型哪个是原图哪个是理想构图==

*参考效果（未控制原因的输出字数）*

<img src="需求分析_detail.assets/image-20260101174402566.png" alt="image-20260101174402566" style="zoom:50%;" />



<span style="font-size:1.2em">**3.流光特效**</span>

**实现原理**：这是一个沿着屏幕边缘流动的梯度光效。

- **Android实现**：
  - 使用 `View` 的 `OutlineProvider` 定义圆角矩形。
  - 编写自定义 `Shader` (LinearGradient 或 SweepGradient)。
  - 通过 `ObjectAnimator` 对 Shader 的矩阵（Matrix）进行旋转或平移变换，产生“流动”的视觉效果。
  - 或者使用 `Lottie` 动画库加载预制的AE动画，这是对本科生最友好的实现方式。

<span style="font-size:1.2em">**4.视觉引导系统**</span>

如何将$I_{cur}$移动到$I_{target}$

**步骤1：获取 $I_{target}$**。如果是Zoom/Shift任务，$I_{target}$ 是云端返回的裁剪图；如果是View-change，可能是云端生成的图（Phase 1可先用裁剪模拟）。

**步骤2：特征提取**。在Android端集成 **OpenCV SDK**。每帧调用 `ORB_create()` 提取关键点。ORB比SIFT快，适合移动端 。

**步骤3：匹配与单应性矩阵**。使用 `BFMatcher` (Brute Force) 进行特征点匹配，然后用 `findHomography` 计算单应性矩阵 $H$ 。

**步骤4：计算偏差**。

- 矩阵 $H$ 包含了旋转和平移信息。
- 通过 `decomposeHomographyMat` 15 可以分解出相机的相对位移向量 $t = [t_x, t_y, t_z]$。
- **逻辑判断**：
  - 若 $t_x > \text{阈值}$，提示“向左移”。
  - 若 $t_z$ 变化明显（或通过特征点尺度的变化），提示“向前/向后移动”。

## 5.2 第二阶段：自主构建与微调

**目标**：摆脱对通用API的依赖，构建针对“构图指导”这一垂直领域的专用模型，提升响应速度和建议的专业度，复现 PhotoFramer 的核心能力。

#### 5.2.1 复现 PhotoFramer 数据集构建流程 

- **Shift & Zoom 数据**：利用现有的裁剪数据集（如GAIC, CPC）。
  - *策略*：取一张高分裁剪图作为 $I_{good}$，然后在其原图上随机偏移裁剪一个低分图作为 $I_{poor}$。这样就构建了一个天然的训练对。
- **View-Change 数据（难点）**：
  - 利用3D数据集（如DL3DV）4。因为3D数据包含同一场景的多个视角。
  - 训练一个**降质模型（Degradation Model）**：输入好视角的图，输出坏视角的图。
  - 将此模型应用到高质量摄影图库（如Unsplash Lite），生成“伪造”的坏构图，作为模型输入的 $I_{poor}$。

#### 5.2.2 模型微调

- **基座模型**：PhotoFramer 原文使用的是 **Bagel** 。也可以考虑使用**Qwen2.5-VL-7B-Instruct** 或 **LLaVA-Next**。
- **训练方法**：使用 LoRA 技术。它允许在消费级显卡（如RTX 3090/4090，甚至Colab Pro）上进行微调，而不需要数百张A100。
- **训练目标**：同时优化文本生成损失（Cross-Entropy Loss，用于生成指导语）和图像生成损失（用于生成示例图）。

### 5.2.3 自主构建与API封装

尝试对模型架构进行修改，自主构建模型和数据集，取得成功后，封装API

## 5.3 第三阶段：APP优化

基于前两阶段的结果，尝试构建 完整的APP，着重优化UI交互与实时性，确保用户使用体验。

# 6.目前遇到的问题

根据主观测试结果来看，google的模型和GPT生成效果比较好。但是存在一些问题

- google有两种图片生成模型，imagen和nano banana，imagen仅为文生图模型，但是支持同时输出多张图片，nano banana为统一架构的多模态模型，支持图生图，但是输出只支持一张图片+文本。

  

  可能的解决方案：异步方式生成多张，采用base提示词+针对不同构图类型的专有提示词
  *Base Prompt:* "Based on the input image, re-compose the scene to create a professional, ideally composed photograph. Keep the main subjects but improve the framing."

  *Direction A (三分法):* "...sl"

  *Direction B (引导线/纵深):* "...Emphasize **leading lines** and depth, drawing the viewer's eye into the background."

  *Direction C (对称/中心):* "...Create a powerful **symmetrical or centered composition**, making the subject the dominant focus."

  *Direction D (特写/紧凑):* "...A tighter, closer frame, focusing on details and textures, removing distracting background elements."



