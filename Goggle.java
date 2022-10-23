package prog11;
import prog05.ArrayQueue;

import javax.swing.*;
import java.util.*;

public class Goggle implements SearchEngine{

    Disk<PageFile> pageDisk;
    Map<String, Long> urlIndex;
    Disk<List<Long>> wordDisk = new Disk<List<Long>>();
    HashMap<String, Long> wordIndex = new HashMap<>();

    Goggle(){
        pageDisk = new Disk<PageFile>();
        urlIndex = new TreeMap<String, Long>();
    }


    public Long indexWord(String word){
        Long index = wordDisk.newFile();
        ArrayList<Long> wordList = new ArrayList<>();
        wordDisk.put(index, wordList);
        wordIndex.put(word, index);
        System.out.println("indexing word " + index + "(" + word + ")" + wordDisk.get(index));
        /*
        Long indexWord = wordIndex.get(word);
        if(indexWord == null) {
            indexWord = wordDisk.newFile();
            List<Long> wordFile = new ArrayList<>();
            wordDisk.put(indexWord, wordFile);
            wordIndex.put(word, indexWord);
            System.out.println("indexing word " + indexWord +"(" + word + ")" + wordFile);
            ArrayList temp = (ArrayList) wordDisk.get(indexWord);
            temp.add(index);
            wordDisk.replace(indexWord, temp);
            System.out.println("added page index " + indexWord + "(" + word + ")" + temp);
        }
        else {
            if(!wordDisk.get(indexWord).contains(index)) {
                ArrayList temp = (ArrayList) wordDisk.get(indexWord);
                temp.add(index);
                wordDisk.replace(indexWord, temp);
                System.out.println("added page index " + indexWord + "(" + word + ")" + temp);
            }
        }

         */
        return index;
    }



    @Override
    public void collect(Browser browser, List<String> startingURLs) {
        Queue queue = new ArrayQueue();

        for(String url : startingURLs) {
            if(!urlIndex.containsKey(url)) {
                queue.offer(indexPage(url));
            }
        }

        while(!queue.isEmpty()) {
            Long pageIndex = (Long)queue.poll();
            PageFile pf = pageDisk.get(pageIndex);
            String url = pf.url;

            if(browser.loadPage(url)) {
                List<String> urls = browser.getURLs();
                List<String> words = browser.getWords();

                for(String curUrl : urls) {
                    if(!urlIndex.containsKey(curUrl)) {
                        queue.offer(indexPage(curUrl));
                    }

                    pf.indices.add(urlIndex.get(curUrl));
                }

                for(String word : words) {
                    Long wIndex = wordIndex.get(word);

                    if(wIndex == null) {
                        wIndex = indexWord(word);
                    }

                    ArrayList<Long> wordPageIndices = (ArrayList<Long>) wordDisk.get(wIndex);

                    if(!wordPageIndices.isEmpty()) {
                        if(!wordPageIndices.get(wordPageIndices.size() - 1).equals(pageIndex)) {
                            wordPageIndices.add(pageIndex);
                        }
                    } else {
                        wordPageIndices.add(pageIndex);
                    }
                }
            }
        }
        /*
        Queue<Long> pageQueue = new LinkedList<>();
        for(String url : startingURLs){
            if(!urlIndex.containsKey(url)){
                Long index = indexPage(url);
                pageQueue.add(index);
            }
        }
        while(!pageQueue.isEmpty()){
            System.out.println("queue " + pageQueue);
            Long index = pageQueue.poll();
            PageFile page = pageDisk.get(index);
            System.out.println("dequeued " + page);
            boolean loaded = browser.loadPage(page.url);
            if(loaded){
                List<String> urls = browser.getURLs();
                System.out.println("urls " + urls);
                for(String url : urls){
                    if(!urlIndex.containsKey(url)){
                        pageQueue.offer(indexPage(url));
                    }
                    page.indices.add(urlIndex.get(url));
                }
            }
            System.out.println("updated " + page);
            System.out.println("words " + browser.getWords());

            for(String word : browser.getWords()) {
                indexWord(word, index);
            }
        }
        */
    }

    public Long indexPage(String url) {
        long index = pageDisk.newFile();
        PageFile newPageFile = new PageFile(index, url);
        pageDisk.put(index, newPageFile);
        urlIndex.put(url, index);
        System.out.println("indexing page " + newPageFile);
        return index;
    }

    protected class Vote implements Comparable<Vote> {
        Long index;
        double vote;

        public Vote(Long index, double vote) {
            this.index = index;
            this.vote = vote;
        }

        @Override
        public int compareTo(Vote o) {
            return index.compareTo(o.index);
        }
    }

    void rankSlow () {
        System.out.println("rank");
        for (PageFile file : pageDisk.values()) {
            file.priority.add(1.0);
        }
        for (int i = 1; i < 20; i++) {
            for (PageFile file : pageDisk.values()) {
                System.out.println(file);
                file.priority.add(0.0);
            }
            System.out.println("---");
            for (PageFile file : pageDisk.values()) {
                double fractionalVote = file.priority.get(i-1) / file.indices.size();
                for (Long index : file.indices) {
                    PageFile file2 = pageDisk.get(index);
                    double newPriority = file2.priority.get(i) + fractionalVote;
                    file2.priority.set(i, newPriority);
                }
            }
        }
    }

    void rankFast () {
        System.out.println("rank");
        for (PageFile file : pageDisk.values())
            file.priority.add(1.0);
        for (int i = 1; i < 20; ++i) {
            for (PageFile file : pageDisk.values())
                System.out.println(file);
            System.out.println("----");

            List<Vote> votes = new ArrayList<Vote>();
            for (PageFile file : pageDisk.values()) {
                double fractionalVote = file.priority.get(i - 1) / file.indices.size();
                for (Long index : file.indices)
                    votes.add(new Vote(index, fractionalVote));
            }

            Collections.sort(votes);
            Iterator<Vote> it = votes.iterator();
            Vote vote = null;
            if (it.hasNext())
                vote = it.next();

            for (PageFile file : pageDisk.values()) {
                double totalVote = 0;
                while (vote != null && vote.index.equals(file.index)) {
                    totalVote += vote.vote;
                    if (it.hasNext())
                        vote = it.next();
                    else
                        vote = null;
                }
                file.priority.add(totalVote);
            }
        }
    }

    @Override
    public String[] search(List<String> searchWords, int numResults) {
        Iterator<Long>[] wordFileIterators = (Iterator<Long>[]) new Iterator[searchWords.size()];
        long[] currentPageIndexes = new long[searchWords.size()];

        for (int i = 0; i < searchWords.size(); i++) {
            wordFileIterators[i] = wordDisk.get(wordIndex.get(searchWords.get(i))).iterator();
        }

        PriorityQueue<Long> bestPageIndexes = new PriorityQueue<>(new PageComparator());
        PageComparator pageComp = new PageComparator();

        while (getNextPageIndexes(currentPageIndexes, wordFileIterators))
            if (allEqual(currentPageIndexes)) {
                System.out.println(pageDisk.get(currentPageIndexes[0]).url);
                if (bestPageIndexes.size() < numResults)
                    bestPageIndexes.offer(currentPageIndexes[0]);
                else if (pageComp.compare(currentPageIndexes[0], bestPageIndexes.peek()) > 0) {
                    bestPageIndexes.poll();
                    bestPageIndexes.offer(currentPageIndexes[0]);
                }
            }
        String[] results = new String[bestPageIndexes.size()];
        for (int i = results.length; --i >= 0;)
            results[i] = pageDisk.get(bestPageIndexes.poll()).url;

        return results;
    }

    private boolean allEqual(long[] array) {
        boolean result = true;
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i] != array[i + 1]) {
                return false;
            }
        }
        return true;
    }

    private long getLargest(long[] array) {
        long largest = array[0];

        for (int i = 0; i < array.length; i++) {
            if (array[i] > largest)
                largest = array[i];
        }
        return largest;
    }

    class PageComparator implements Comparator<Long> {

        @Override
        public int compare(Long pageIndex1, Long pageIndex2) {
            List<Double> list1 = pageDisk.get(pageIndex1).priority;
            List<Double> list2 = pageDisk.get(pageIndex2).priority;
            double diff = list1.get(list1.size() - 1) - list2.get(list1.size() - 1);
            if (diff < 0)
                return -1;
            else if (diff > 0)
                return 1;
            return 0;
        }
    }

    private boolean getNextPageIndexes(long[] currentPageIndexes, Iterator<Long>[] wordFileIterators) {
        try {
            boolean equals = allEqual(currentPageIndexes);
            if(equals) {
                for (int i = 0; i < currentPageIndexes.length ; i++) {
                    Iterator<Long> iter = wordFileIterators[i];
                    currentPageIndexes[i] = iter.next();
                }
                return true;
            }else {

                long biggest = -1;
                for (int i = 0; i < currentPageIndexes.length; i++) {
                    if (currentPageIndexes[i] > biggest)
                        biggest = currentPageIndexes[i];
                }
                for (int i = 0; i < wordFileIterators.length; i++) {
                    Iterator<Long> iterator = wordFileIterators[i];
                    if (currentPageIndexes[i] < biggest) {
                        currentPageIndexes[i] = iterator.next();
                    }
                }
                return true;
            }

        } catch(NoSuchElementException e) {
            return false;
        }
    }
}