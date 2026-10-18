# Hookshot Crossbow (Fabric, Minecraft Java 26.3)

クロスボウ型のフックショット。撃つとフックが飛び、ロープ(バニラのリード)でつながり、
命中した場所までスーーッと滑らかに引っ張られます。使用後は自動で再装填。

## 操作
- 右クリック: フック発射
- フック中にもう一度右クリック / ジャンプ: ロープを離す(ジャンプは小ジャンプ付き)
- スニーク + 右クリックでモブを狙う: モブを引き寄せる
- 命中したらロープに引かれて自動で移動 → 到着すると自動で再装填(「カチッ」と音)

入手: クリエイティブの「戦闘」タブ、または `/give @s hookshot:hookshot`

## ビルド
必要: JDK 25 / Gradle 9.6.0(または Gradle ラッパー)
```
gradle wrapper --gradle-version 9.6.0     # 初回のみ
./gradlew build                           # build/libs/hookshot-1.0.0.jar ができる
./gradlew runClient                       # 開発環境で起動して試す
```
できた jar を Fabric Loader 0.19.5 + Fabric API 0.163.0+26.3 の mods フォルダへ。

## サーバーで遊ぶ場合
フック中は空中に浮いた状態になるので、server.properties で `allow-flight=true` が必要です
(シングルプレイでは不要)。

## 調整
- 速度: `client/HookClient.java` の `MAX_SPEED`, `RAMP_TICKS`, `STEER`
- 射程・再装填: `HookEntity.java` の `MAX_RANGE`, `RELOAD_TICKS`
