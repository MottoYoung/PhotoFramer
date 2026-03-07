# main
主分支,包含各版本的前后端,应该是能直接运行的版本

# feat:backend/serial
串行分支,只追踪pc_demo_serial的修改
# feat:backend/parallel
并行分支,只追踪pc_demo_parallel的修改
# feat:frontend
前端分支,只追踪PhotoFramer/android_frontend的修改
# feat:prompt_test
prompt测试分支,只追踪PhotoFramer/Gemini_prompt_tests的修改


分支使用方法:

## 1. 初次加入项目 (Clone)
当你或者你的队友**第一次**拿到这个项目，或者换了一台新电脑时，需要整个搬运：
```bash
git clone https://github.com/MottoYoung/PhotoFramer.git
```
*`clone`：相当于“下载包圆”，把整个远端仓库的当前状态连带它所有的历史记录、所有的分支，全部复制到本地电脑上。一辈子通常只对一个项目执行一次。*

## 2. 日常同步更新 (Pull)
当你的本地已经有了项目代码，只是想看看**别人有没有提交新代码**并合并到自己的电脑上：
```bash
# 确保你在想要更新的分支上
git checkout main
git pull
```
*`pull`：相当于“下载更新包”，只把远端仓库自你上次同步以来产生的**增量修改**拉下来，并与你本地当前的代码合并。每天写代码前都建议顺手 pull 一下。*

## 3. 怎样基于分支开发新功能
1. **永远基于最新的 main 开发：**
   ```bash
   git checkout main
   git pull
   ```
2. **创建或切换到子分支：**
   ```bash
   # 场景 A：如果远端没有这个前端分支，你需要自己在本地【新建】
   git checkout -b feat:frontend
   
   # 场景 B：如果远端已经有别人建好的前端分支，你只需要【切换】进去
   git checkout feat:frontend
   ```
3. **改完代码提交并推送到远端：**
   ```bash
   git add .
   git commit -m "更新了前端某个功能"
   # 如果是第一次推送该分支，使用 -u 绑定
   git push -u ori feat:frontend
   ```
4. **合并分支 (Merge) ：**
   当分支开发完毕，且在本地测试无误后，你得把它并入 `main` 以发布。有两种方式：
   
   **方式 A：通过 GitHub 网页合并（推荐多人协作）**
   - 登录 GitHub，仓库顶部会提示有新分支推送，点击 `Compare & pull request (PR)`
   - 队友审核代码，点击绿色的 `Merge pull request`
   - 本地电脑切回主枝并更新：
     ```bash
     git checkout main
     git pull
     # 删除已经没用的本地开发分支
     git branch -d feat:frontend
     ```

   **方式 B：本地自己合并（适合个人项目开发）**
   - 如果是你自己做完了一个分支，可以直接在本地把分支拍到 `main` 里，再推上去：
     ```bash
     git checkout main       # 切回主干
     git merge feat:frontend # 把前端分支合并到现在的 main 里
     git push                # 推送合并后的 main 到云端
     git branch -d feat:frontend # 顺手清理垃圾分支
     ```