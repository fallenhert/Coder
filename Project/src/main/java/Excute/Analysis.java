package Excute;

import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class Analysis {
    /**
     *
     * @description : 图像生成
     * @param : scope 分析域
     * @return : CHACallGraph 图像
     */
    public static CHACallGraph GraphMake(AnalysisScope scope) throws WalaException, CancelException {
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);
        CHACallGraph cg = new CHACallGraph(cha);
        cg.init(eps);
        System.out.println("cg complete");
        return cg;
    }

    /**
     *
     * @description : 读取文件，主要为change_info
     * @param change_info : change_info输入路径
     * @return ArrayList<String>
     */
    public static ArrayList<String> changeInfoRead(String change_info){
        ArrayList<String> change_info_message = new ArrayList<>();
        try (FileReader reader = new FileReader(change_info);
             BufferedReader br = new BufferedReader(reader)
        ) {
            String line;
            while ((line = br.readLine()) != null) {
                change_info_message.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return change_info_message;
    }

    /**
     *
     * @description : 输出txt文件
     * @param message : 输出信息
     * @param outPutPath : 输出路径
     * @return ArrayList<String>
     */
    public static void writeFile(ArrayList<String> message,String outPutPath) {
        try {
            File writeName = new File(outPutPath);
            writeName.createNewFile();
            try (FileWriter writer = new FileWriter(writeName);
                 BufferedWriter out = new BufferedWriter(writer)) {
                for(String mes:message){
                    out.write(mes+"\n");
                }
                out.flush(); // 把缓存区内容压入文件
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @description : 获取本地Jar包路径
     */
    public static String getLocalJarPath(){
        String path = Analysis.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        try
        {
            path = java.net.URLDecoder.decode(path, "UTF-8"); // 处理中文
            File file = new File(path);
            return file.getParent();
        }
        catch (java.io.UnsupportedEncodingException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @description : 获取类图
     * @param cg : 图像
     * @return void 返回值为空 输出到class-cfa.dot
     */
    public static ArrayList<String> getClassDot(CHACallGraph cg){
        ArrayList<String> classDot = new ArrayList<>();
        ArrayList<String> classRelation = new ArrayList<>();
        classDot.add("digraph class {");
        for(CGNode node:cg){
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();
                    if(!signature.contains("Test") && !signature.contains("$")){
                        Iterator<CGNode> predNodes= cg.getPredNodes(node);
                        while(predNodes.hasNext()){
                            CGNode nextNode = predNodes.next();
                            if("Application".equals(nextNode.getMethod().getDeclaringClass().getClassLoader().toString())) {
                                String nextClassInnerName=nextNode.getMethod().getDeclaringClass().getName().toString();
                                if(!classDot.contains("\t\""+classInnerName+"\" -> \""+nextClassInnerName+"\";")){
                                    classDot.add("\t\""+classInnerName+"\" -> \""+nextClassInnerName+"\";");
                                    classRelation.add(classInnerName+" "+nextClassInnerName);
                                }
                            }
                        }
                    }
                }
            }
        }
        classDot.add("}");
        writeFile(classDot,"class-cfa.dot");
        System.out.println("class-cfa.dot build");
        return classRelation;
    }

    /**
     * @description : 获取方法图
     * @param cg : 图像
     * @return void 返回值为空 输出到method-cfa.dot
     */
    public static ArrayList<String> getMethodDot(CHACallGraph cg){
        ArrayList<String> methodDot = new ArrayList<>();
        ArrayList<String> methodRelation = new ArrayList<>();
        methodDot.add("digraph method {");
        for(CGNode node:cg){
            if(node.getMethod() instanceof ShrikeBTMethod){
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String signature = method.getSignature();
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    if(classInnerName.contains("Test")||classInnerName.contains("$"))continue;
                    Iterator<CGNode> predNodes= cg.getPredNodes(node);
                    while(predNodes.hasNext()){
                        CGNode next =predNodes.next();
                        if("Application".equals(next.getMethod().getDeclaringClass().getClassLoader().toString())) {
                            String nextSignature=next.getMethod().getSignature();
                            if(!methodDot.contains("\t\""+signature+"\" -> \""+nextSignature+"\";")){
                                methodDot.add("\t\""+signature+"\" -> \""+nextSignature+"\";");
                                methodRelation.add(signature+" "+nextSignature);
                            }
                        }
                    }
                }
            }
        }
        methodDot.add("}");
        writeFile(methodDot,"method-cfa.dot");
        System.out.println("method-cfa.dot build");
        return methodRelation;
    }

    /**
     * @description : 获取类方法的处理结果
     * @param cg : 分析域图
     * @param change_info : 变更记录
     * @param classRelation : 类关联
     */
    public static void ExcuteC(CHACallGraph cg, ArrayList<String> change_info,ArrayList<String> classRelation){
        ArrayList<String> selectClass = new ArrayList<>();
        ArrayList<String> changedClass = new ArrayList<>();
        for(String relation:classRelation){
            String classInnerName = relation.split(" ")[0];
            String nextClassInnerName = relation.split(" ")[1];
            for(String change:change_info){
                if(classInnerName.compareTo(change.split(" ")[0])==0 &&
                        nextClassInnerName.contains("Test")){
                    changedClass.add(nextClassInnerName);
                }
            }
        }

        for(CGNode node:cg){
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();
                    if(changedClass.contains(classInnerName)&& !signature.contains("<init>")
                            &&!signature.contains("initialize()") && !selectClass.contains(classInnerName+" "+signature)){
                        selectClass.add(classInnerName+" "+signature);
                    }
                }
            }
        }
        writeFile(selectClass,"selection-class.txt");
    }

    /**
     * @description : 获取图方法的处理结果
     * @param cg : 分析域图
     * @param change_info : 变更记录
     */
    public static void ExcuteM(CHACallGraph cg, ArrayList<String> change_info){
        ArrayList<String> selectMethod = new ArrayList<>();
        for(CGNode node:cg){
            if(node.getMethod() instanceof ShrikeBTMethod){
                ShrikeBTMethod method = (ShrikeBTMethod)node.getMethod();
                if("Application".equals(method.getDeclaringClass().getClassLoader().toString())){
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();
                    if(signature.contains("Test") && containMethod(cg,node,change_info) && !signature.contains("<init>")){
                        if(!selectMethod.contains(classInnerName+" "+signature)){
                            selectMethod.add(classInnerName+" "+signature);
                        }
                    }
                }
            }
        }
        writeFile(selectMethod,"selection-method.txt");
    }

    private static Boolean containMethod(CHACallGraph cg, CGNode node, List<String> changes){
        Iterator<CGNode> succNodes=cg.getSuccNodes(node);
        if(!succNodes.hasNext()) return false;
        Boolean result=false;
        while(succNodes.hasNext()){
            CGNode x=succNodes.next();
            if (!"Application".equals(x.getMethod().getDeclaringClass().getClassLoader().toString())) continue;
            String succSignature = x.getMethod().getSignature();
            for(String single:changes){
                if(single.split(" ")[1].compareTo(succSignature)==0){
                    return true;
                }
            }
            result = containMethod(cg,x,changes);
            if(result){
                break;
            }
        }
        return result;
    }


    //        指令一： java -jar testSelection.jar -c <project_target> <change_info>，执行类级测试选择；
    //        指令二： java -jar testSelection.jar -m <project_target> <change_info>。执行方法级测试选择；
    public static void main(String[] args) throws WalaException, CancelException, IOException, InvalidClassFileException {
        String[] classes = {"0-CMD","1-ALU","2-DataLog","3-BinaryHeap","4-NextDay","5-MoreTriangle"};
        String project_target =
                "D:\\大三上\\自动化测试\\大作业\\经典大作业\\ClassicAutomatedTesting\\ClassicAutomatedTesting\\"
                        +classes[0]+"\\target";
        String change_info =
                "D:\\大三上\\自动化测试\\大作业\\经典大作业\\ClassicAutomatedTesting\\ClassicAutomatedTesting\\"
                        +classes[0]+"\\data\\change_info.txt";
        AnalysisScope scope = scopeBuild.scopeBuild.buildScope(project_target);
        CHACallGraph cg = GraphMake(scope);
        ArrayList<String> classRelation = getClassDot(cg);
        ArrayList<String> methodRelation = getMethodDot(cg);
        ArrayList<String> mesg = changeInfoRead(change_info);
        System.out.println("change_info loaded");
        ExcuteC(cg,mesg,classRelation);
        ExcuteM(cg,mesg);
        System.out.println("complete");
//        ArrayList<String> mine = changeInfoRead("D:\\大三上\\自动化测试\\Coder\\Project\\method-cfa.dot");
//        ArrayList<String> gets = changeInfoRead("D:\\大三上\\自动化测试\\Coder\\Data\\ClassicAutomatedTesting\\0-CMD\\data\\method-CMD-cfa.dot");
//        for(String str :mine){
//            if(!gets.contains(str)){
//                System.out.println(str);
//            }
//        }
//        for(String str :gets){
//            if (!mine.contains(str)){
//                System.out.println(str);
//            }
//        }
    }
}
